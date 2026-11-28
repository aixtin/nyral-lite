package io.github.aixtin.nyrallite.plugins.js

import android.content.Context
import io.github.aixtin.nyrallite.contract.ToolResult

/**
 * JS 工具执行：按 id 加载脚本，按 manifest 声明装配能力网关，跑完即焚。
 *
 * 能力装配只认 manifest：声明清单之外的能力即使宿主有实现也不接线——
 * 安装确认时用户看到什么能力，运行时就只有什么能力。
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
