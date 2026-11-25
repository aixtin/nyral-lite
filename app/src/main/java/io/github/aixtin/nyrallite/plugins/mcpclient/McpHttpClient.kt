package io.github.aixtin.nyrallite.plugins.mcpclient

import io.github.aixtin.nyrallite.contract.McpConfig
import java.net.HttpURLConnection
import java.net.URL

/** 一次 POST 的结果：响应体 + 服务端分发的会话 id（新会话时才有值）。 */
internal class McpHttpResult(val body: String, val sessionId: String?)

/**
 * MCP Streamable HTTP 的最小客户端：每个 JSON-RPC 报文一次 POST。
 * 不处理 GET 长连接与重连，够覆盖本 App 的请求-响应式工具调用。
 */
internal class McpHttpClient(private val config: McpConfig) {

    fun post(body: String, sessionId: String?): McpHttpResult {
        val conn = (URL(config.serverUrl.trim()).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json, text/event-stream")
            if (!sessionId.isNullOrBlank()) setRequestProperty("Mcp-Session-Id", sessionId)
        }
        applyAuthHeader(conn)
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw RuntimeException("MCP HTTP $code: ${text.trim().take(200)}")
            }
            return McpHttpResult(text, conn.getHeaderField("Mcp-Session-Id"))
        } finally {
            try { conn.disconnect() } catch (_: Exception) {}
        }
    }

    /** 名称和值都填了才发送；留空表示服务器不需要额外鉴权。 */
    private fun applyAuthHeader(conn: HttpURLConnection) {
        val name = config.headerName.trim()
        val value = config.headerValue.trim()
        if (name.isNotEmpty() && value.isNotEmpty()) {
            conn.setRequestProperty(name, value)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        /** 远程工具可能执行很久，读超时给足。 */
        const val READ_TIMEOUT_MS = 180_000
    }
}
