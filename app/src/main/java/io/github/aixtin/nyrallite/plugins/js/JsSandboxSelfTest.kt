package io.github.aixtin.nyrallite.plugins.js

import android.util.Log
import io.github.aixtin.nyrallite.contract.ToolResult

/**
 * JS 沙箱自测（M1）：一段固定脚本验证三件事——
 * 1. 裸机：fetch/process/require 都不存在（结构性沙箱）；
 * 2. 计算正常：1+2=3；
 * 3. 能力插座：nyral.invoke("http_fetch") 经网关拿到真实响应。
 */
internal object JsSandboxSelfTest {
    private val SCRIPT = """
        const bare = typeof fetch === "undefined"
          && typeof process === "undefined"
          && typeof require === "undefined";
        const sum = 1 + 2;
        let fetched = null;
        try {
          const r = nyral.invoke("http_fetch", JSON.stringify({ url: "https://example.com" }));
          fetched = r === null || r === undefined ? "denied" : "len=" + r.length;
        } catch (e) {
          fetched = "err:" + e;
        }
        JSON.stringify({ bare, sum, fetched });
    """.trimIndent()

    /** 阻塞执行（含网络），调用方须在 IO 线程。 */
    fun run(httpFetcher: ((url: String) -> ByteArray?)?): ToolResult {
        val gateway = JsPluginGateway(httpFetcher = httpFetcher)
        return try {
            val out = gateway.run(SCRIPT)
            Log.i("JsSandbox", "selftest out=$out audit=${gateway.auditLog}")
            if (out != null) ToolResult(content = out)
            else ToolResult(content = "engine returned null", isError = true)
        } catch (t: Throwable) {
            ToolResult(content = t.message ?: "unknown", isError = true)
        }
    }
}
