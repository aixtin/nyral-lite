package io.github.aixtin.nyrallite.plugins.js

import android.content.Context
import io.github.aixtin.nyrallite.contract.ApprovalDecision
import io.github.aixtin.nyrallite.contract.SecurityMode
import io.github.aixtin.nyrallite.contract.ToolApproval
import io.github.aixtin.nyrallite.contract.ToolResult
import io.github.aixtin.nyrallite.contract.ToolSource
import io.github.aixtin.nyrallite.contract.ToolSpec
import io.github.aixtin.nyrallite.plugins.wasm.WasmPluginRuntime
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 模型自造工具铸造器：模型给自己写 JS 工具的入口，两个工具——
 *
 *  write_js_tool(toolId, script)：脚本落盘为草稿（不登记信任）。调试期免弹窗是既定口径：
 *  生成过程对话内可见 + QuickJS 沙箱与能力网关兜底 + 能力不超模型自身。
 *  finalize_js_tool(toolId)：定稿确认。默认复用主体的 [ToolApproval] 通道弹一次窗，
 *  用户允许才登记哈希；登记后脚本文件一旦被改动，运行时校验会拒绝并要求重新定稿。
 *  门禁放行档（[SecurityMode.OFF]）下免弹窗直接登记——用户已声明不拦截工具操作，
 *  再弹定稿确认就是骚扰；询问/严格档仍弹窗。
 *
 *  防骚扰：用户拒绝定稿的工具记入本进程拒绝表，同一会话内模型再提交直接拒绝并告知。
 */
class ToolForgeSource(
    private val appContext: Context,
    private val approval: ToolApproval,
    /** 当前门禁档位；每次定稿时现查，改档位立即生效 */
    private val securityMode: () -> SecurityMode = { SecurityMode.AUTO },
) : ToolSource {

    override val sourceId: String = "builtin:tool-forge"

    /** 用户拒绝过定稿的工具 id；进程级（重启即清空），只用于防模型反复提交 */
    private val denied = ConcurrentHashMap.newKeySet<String>()

    override suspend fun list(): List<ToolSpec> = FORGE_TOOLS

    override suspend fun owns(name: String): Boolean =
        name == TOOL_WRITE || name == TOOL_FINALIZE

    override suspend fun callOrNull(name: String, argumentsJson: String): ToolResult? =
        withContext(Dispatchers.IO) {
            when (name) {
                TOOL_WRITE -> write(argumentsJson)
                TOOL_FINALIZE -> finalize(argumentsJson)
                else -> null
            }
        }

    private fun write(argumentsJson: String): ToolResult {
        val args = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return ToolResult("参数不是合法 JSON 对象", isError = true)
        val toolId = args.optString("toolId")
        val script = args.optString("script")
        if (toolId in denied) {
            return ToolResult(
                "用户已拒绝工具 $toolId，本次会话内不要再提交、重写或再次请求定稿该工具",
                isError = true,
            )
        }
        if (!TOOL_ID_REGEX.matches(toolId)) {
            return ToolResult(
                "toolId 不合法：须为小写字母开头，只含小写字母、数字、下划线，最长 32 字符",
                isError = true,
            )
        }
        if (script.isBlank()) {
            return ToolResult("script 不能为空", isError = true)
        }
        // 元数据硬校验前置：不合格的脚本不落盘，把拒绝原因回给模型迭代
        val reason = when (val m = parseJsToolMeta(script)) {
            is JsToolMetaResult.Ok -> null
            is JsToolMetaResult.Fail -> m.reason
        }
        if (reason != null) {
            return ToolResult(
                "脚本未通过校验：$reason（正确格式：第一行 " +
                    "/* nyral.tool {\"name\":\"..\",\"description\":\"..\",\"contractVersion\":\"1.0\",\"capabilities\":[..]} */）",
                isError = true,
            )
        }
        return if (JsToolRepo.install(appContext, toolId, script)) {
            ToolResult(
                "草稿已保存：${JsToolSource.toolName(toolId)}。" +
                    "可直接调用 ${JsToolSource.toolName(toolId)} 试跑（草稿试跑不弹窗）。" +
                    "确认可用后调用 finalize_js_tool 定稿" +
                    "（默认弹窗请用户确认；门禁放行档自动登记，不弹窗）。",
            )
        } else {
            ToolResult("草稿保存失败：工具文件写入异常", isError = true)
        }
    }

    private suspend fun finalize(argumentsJson: String): ToolResult {
        val args = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return ToolResult("参数不是合法 JSON 对象", isError = true)
        val toolId = args.optString("toolId")
        if (toolId in denied) {
            return ToolResult(
                "用户已拒绝工具 $toolId，本次会话内不要再提交、重写或再次请求定稿该工具",
                isError = true,
            )
        }
        val script = JsToolRepo.load(appContext, toolId)
            ?: return ToolResult("工具不存在：$toolId（先用 write_js_tool 写草稿）", isError = true)
        val meta = when (val m = parseJsToolMeta(script)) {
            is JsToolMetaResult.Ok -> m.meta
            is JsToolMetaResult.Fail -> return ToolResult("脚本未通过校验：${m.reason}", isError = true)
        }
        val sha = WasmPluginRuntime.sha256(script.toByteArray(Charsets.UTF_8))
        val registered = JsToolStore(appContext).get(toolId)
        if (registered != null && registered.equals(sha, ignoreCase = true)) {
            return ToolResult("工具 $toolId 已定稿且脚本未变化，无需重复操作")
        }
        // 放行档 = 用户声明不拦截工具操作，定稿确认一并免掉，直接登记信任
        if (securityMode() == SecurityMode.OFF) {
            JsToolStore(appContext).put(toolId, sha)
            return ToolResult(
                "已定稿：${JsToolSource.toolName(toolId)} 已登记信任（门禁放行档，免确认）。" +
                    "之后脚本文件一旦被改动，运行会被拒绝，需重新定稿。",
            )
        }
        val decision = approval.ask(
            sourceId,
            "定稿 JS 工具 $toolId",
            JSONObject().apply {
                put("工具", toolId)
                put("描述", meta.description)
                put(
                    "能力声明",
                    if (meta.capabilities.isEmpty()) "无（纯计算工具）" else meta.capabilities.joinToString("、"),
                )
                put("文件哈希", sha.uppercase())
            }.toString(),
        )
        return when (decision) {
            ApprovalDecision.ALLOW_ONCE,
            ApprovalDecision.ALLOW_SESSION,
            ApprovalDecision.ALLOW_ALWAYS,
            -> {
                JsToolStore(appContext).put(toolId, sha)
                ToolResult(
                    "已定稿：${JsToolSource.toolName(toolId)} 已登记信任。" +
                        "之后脚本文件一旦被改动，运行会被拒绝，需重新定稿。",
                )
            }
            ApprovalDecision.DENY,
            ApprovalDecision.DENY_ALWAYS,
            -> {
                denied.add(toolId)
                ToolResult(
                    "用户拒绝定稿工具 $toolId。本次会话内不要再提交、重写或再次请求定稿该工具",
                    isError = true,
                )
            }
            ApprovalDecision.UNAVAILABLE ->
                ToolResult("确认通道不可用（当前没有前台界面），请稍后重试", isError = true)
        }
    }

    companion object {
        const val TOOL_WRITE = "write_js_tool"
        const val TOOL_FINALIZE = "finalize_js_tool"

        /** 与 JsToolMeta 的能力名同规：小写字母开头，小写字母/数字/下划线，最长 32 字符 */
        private val TOOL_ID_REGEX = Regex("^[a-z][a-z0-9_]{0,31}$")

        private val FORGE_TOOLS = listOf(
            ToolSpec(
                name = TOOL_WRITE,
                description = "把你写的 JS 工具脚本保存为草稿。脚本第一行必须是元数据注释块：" +
                    "/* nyral.tool {\"name\":\"展示名\",\"description\":\"一句话描述\",\"contractVersion\":\"1.0\",\"capabilities\":[\"http_fetch\"]} */" +
                    "（JSON 紧跟 nyral.tool 之后，不要写成 Javadoc 的键值行）。" +
                    "脚本在 QuickJS 全局作用域运行：宿主入口 nyral.invoke(\"http_fetch\", JSON.stringify({url:\"...\"})) " +
                    "返回响应体字符串或 null；顶层不能用 return，脚本最后一个表达式的值（JSON 序列化后）就是工具结果。" +
                    "参数直接烧进脚本，工具本身零参数。保存后即可试跑，定稿前不弹窗。",
                parametersJson = PARAMS_WRITE,
            ),
            ToolSpec(
                name = TOOL_FINALIZE,
                description = "把你写好的 JS 工具草稿定稿：默认弹窗请用户确认一次，确认后工具受信任长期可用；" +
                    "门禁放行档下自动登记不弹窗。" +
                    "定稿后脚本文件被改动会导致运行被拒，需重新定稿。用户拒绝过的工具不要再提交。",
                parametersJson = PARAMS_FINALIZE,
            ),
        )

        private const val PARAMS_WRITE =
            "{\"type\":\"object\",\"properties\":{\"toolId\":{\"type\":\"string\",\"description\":\"工具 id：小写字母开头的小写字母/数字/下划线，最长 32 字符\"},\"script\":{\"type\":\"string\",\"description\":\"完整 JS 脚本，头部含 nyral.tool 元数据注释块\"}},\"required\":[\"toolId\",\"script\"]}"

        private const val PARAMS_FINALIZE =
            "{\"type\":\"object\",\"properties\":{\"toolId\":{\"type\":\"string\"}},\"required\":[\"toolId\"]}"
    }
}
