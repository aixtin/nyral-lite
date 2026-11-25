package io.github.aixtin.nyrallite.plugins.mcpclient

import io.github.aixtin.nyrallite.contract.McpConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一次 MCP 会话：initialize 握手 → tools/list → tools/call。
 *
 * 会话 id 由服务端在 initialize 响应头里下发，后续请求必须带回；
 * 方法全部串行（@Synchronized），避免并发请求把 id 与握手状态打乱。
 */
internal class McpSession(private val config: McpConfig) {

    private val client = McpHttpClient(config)
    private var sessionId: String? = null
    private var nextId = 1L
    private var initialized = false

    @Synchronized
    fun listTools(): JSONArray {
        ensureInitialized()
        return rpc("tools/list", JSONObject()).optJSONArray("tools") ?: JSONArray()
    }

    @Synchronized
    fun callTool(name: String, argumentsJson: String): JSONObject {
        ensureInitialized()
        val arguments = try {
            JSONObject(argumentsJson)
        } catch (_: Exception) {
            JSONObject()
        }
        val params = JSONObject().put("name", name).put("arguments", arguments)
        return rpc("tools/call", params)
    }

    private fun ensureInitialized() {
        if (initialized) return
        val params = JSONObject()
            .put("protocolVersion", PROTOCOL_VERSION)
            .put("capabilities", JSONObject())
            .put("clientInfo", JSONObject().put("name", "NyralLite").put("version", "0.1.0"))
        rpc("initialize", params)
        // 初始化通知没有 id、也可能返回空体，失败不应拖垮整个会话
        try {
            client.post(McpJsonRpc.notification("notifications/initialized"), sessionId)
                .sessionId?.let { sessionId = it }
        } catch (_: Exception) {
            // 部分实现只在 initialize 时校验会话，通知失败可以忽略
        }
        initialized = true
    }

    private fun rpc(method: String, params: JSONObject): JSONObject {
        val id = nextId++
        val response = client.post(McpJsonRpc.request(id, method, params), sessionId)
        response.sessionId?.let { sessionId = it }
        return McpJsonRpc.resultOf(response.body, id)
    }

    private companion object {
        const val PROTOCOL_VERSION = "2025-06-18"
    }
}
