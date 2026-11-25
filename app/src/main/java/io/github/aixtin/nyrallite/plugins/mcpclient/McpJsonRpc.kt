package io.github.aixtin.nyrallite.plugins.mcpclient

import org.json.JSONObject

/**
 * JSON-RPC 2.0 报文构造与响应提取。
 * 只处理 MCP 用到的最小集：请求、通知、按 id 找结果。
 */
internal object McpJsonRpc {

    fun request(id: Long, method: String, params: JSONObject?): String = JSONObject()
        .put("jsonrpc", "2.0")
        .put("id", id)
        .put("method", method)
        .apply { if (params != null) put("params", params) }
        .toString()

    fun notification(method: String): String = JSONObject()
        .put("jsonrpc", "2.0")
        .put("method", method)
        .toString()

    /**
     * 从响应体里找 id 对应的 result。
     * 兼容两种返回：纯 JSON 对象，或 Streamable HTTP 的 SSE 事件流（每个 data 行一条消息）。
     */
    fun resultOf(body: String, id: Long): JSONObject {
        for (message in messagesOf(body)) {
            val obj = try {
                JSONObject(message)
            } catch (_: Exception) {
                continue
            }
            if (obj.optLong("id", -1L) != id) continue
            obj.optJSONObject("error")?.let { error ->
                throw RuntimeException(error.optString("message").ifBlank { error.toString() })
            }
            return obj.optJSONObject("result") ?: JSONObject()
        }
        throw RuntimeException("MCP 响应缺少 id=$id 的结果")
    }

    private fun messagesOf(body: String): List<String> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (!trimmed.startsWith("data:")) return listOf(trimmed)
        return trimmed.lineSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }
}
