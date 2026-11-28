package io.github.aixtin.nyrallite.plugins.js

import android.content.Context
import io.github.aixtin.nyrallite.contract.ToolResult
import io.github.aixtin.nyrallite.plugins.wasm.WasmPluginRuntime

/**
 * JS 工具执行：按 id 加载脚本，按 manifest 声明装配能力网关，跑完即焚。
 *
 * 能力装配只认 manifest：声明清单之外的能力即使宿主有实现也不接线——
 * 安装确认时用户看到什么能力，运行时就只有什么能力。
 *
 * 运行前做信任校验：已登记的工具，文件哈希与登记不一致即拒绝（防篡改，
 * 不论改动来自谁）；未登记的视为草稿（模型自造调试期），放行。
 */
internal object JsToolRunner {

    /** 阻塞执行（含网络），调用方须在 IO 线程。 */
    fun run(
        context: Context,
        toolId: String,
        httpFetcher: ((url: String) -> ByteArray?)?,
    ): ToolResult {
        val script = JsToolRepo.load(context, toolId)
            ?: return ToolResult(content = "脚本文件缺失：$toolId", isError = true)
        val actualSha = WasmPluginRuntime.sha256(script.toByteArray(Charsets.UTF_8))
        when (JsToolTrust.check(JsToolStore(context).get(toolId), actualSha)) {
            JsToolTrustResult.Changed -> return ToolResult(
                content = "文件已变更，需重新确认：工具 $toolId 的脚本与信任登记不一致，已拒绝运行",
                isError = true,
            )
            JsToolTrustResult.Pass -> {}
        }
        val meta = when (val r = parseJsToolMeta(script)) {
            is JsToolMetaResult.Ok -> r.meta
            is JsToolMetaResult.Fail -> return ToolResult(content = r.reason, isError = true)
        }
        val gateway = JsPluginGateway(
            capabilities = meta.capabilities.toSet(),
            httpFetcher = httpFetcher,
        )
        return try {
            val out = gateway.run(script)
            if (out != null) ToolResult(content = out)
            else ToolResult(content = "引擎返回空结果", isError = true)
        } catch (t: Throwable) {
            ToolResult(content = t.message ?: "unknown", isError = true)
        }
    }
}
