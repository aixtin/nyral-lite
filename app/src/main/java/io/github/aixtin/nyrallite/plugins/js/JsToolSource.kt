package io.github.aixtin.nyrallite.plugins.js

import android.content.Context
import io.github.aixtin.nyrallite.contract.ToolResult
import io.github.aixtin.nyrallite.contract.ToolSource
import io.github.aixtin.nyrallite.contract.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JS 工具来源：把已装 JS 工具（含模型自造的草稿）注册成模型可调用的工具。
 *
 * 与 WasmToolSource 平级，由 MultiToolRegistry 聚合；调用统一过 GateBus 门禁，
 * 工具内部再过运行时信任校验（已登记哈希比对）与能力网关。
 *
 * 工具名格式：js_<工具id>。模型自造工具把参数烧进脚本，工具本身零参数；
 * 需要变参时模型重写脚本再定稿。元数据解析失败的工具不暴露（与 WASM 同口径）。
 *
 * 名字解析直接查文件系统而非 list() 时的缓存：同一轮对话里刚定稿的工具
 * （write_js_tool → finalize_js_tool）还没等到下一次 list()，也能立刻被调用。
 */
class JsToolSource(
    private val appContext: Context,
    private val httpFetcher: ((url: String, offset: Int) -> ByteArray?)? = null,
) : ToolSource {

    override val sourceId: String = "builtin:js-tools"

    override suspend fun list(): List<ToolSpec> = withContext(Dispatchers.IO) {
        val specs = ArrayList<ToolSpec>()
        val registered = HashSet<String>()
        val store = JsToolStore(appContext)
        for (toolId in JsToolRepo.list(appContext)) {
            val script = JsToolRepo.load(appContext, toolId) ?: continue
            val meta = when (val m = parseJsToolMeta(script)) {
                is JsToolMetaResult.Ok -> m.meta
                is JsToolMetaResult.Fail -> continue
            }
            val name = toolName(toolId)
            // 清洗后撞名的工具先到先得，与 MultiToolRegistry 的去重语义一致
            if (registered.add(name)) {
                val draft = store.get(toolId) == null
                specs += ToolSpec(
                    name = name,
                    description = if (draft) "（草稿，未定稿）${meta.description}" else meta.description,
                    parametersJson = PARAMS_EMPTY,
                )
            }
        }
        specs
    }

    override suspend fun owns(name: String): Boolean = resolve(name) != null

    override suspend fun callOrNull(name: String, argumentsJson: String): ToolResult? {
        val toolId = resolve(name) ?: return null
        return withContext(Dispatchers.IO) {
            JsToolRunner.run(appContext, toolId, httpFetcher)
        }
    }

    /** 模型侧工具名 → 工具 id；每次现查文件系统，不依赖 list() 的调用时机 */
    private fun resolve(name: String): String? =
        resolveToolId(JsToolRepo.list(appContext), name)

    companion object {
        const val TOOL_PREFIX = "js_"

        private val NOT_ALLOWED = Regex("[^a-zA-Z0-9_-]")

        private const val PARAMS_EMPTY = "{\"type\":\"object\",\"properties\":{}}"

        /** 工具 id 清洗后拼前缀；id 本身受铸造器约束，清洗只为兜住 SAF 导入的任意文件名 */
        fun toolName(toolId: String): String = "$TOOL_PREFIX${toolId.replace(NOT_ALLOWED, "_")}"

        /** 按字母序先到先得：清洗撞名时排在前面的工具 id 胜出，与 list() 的去重一致 */
        fun resolveToolId(toolIds: List<String>, name: String): String? =
            toolIds.firstOrNull { toolName(it) == name }
    }
}
