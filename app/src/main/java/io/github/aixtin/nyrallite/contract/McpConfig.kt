package io.github.aixtin.nyrallite.contract

/**
 * 远程 MCP 服务器配置：Streamable HTTP 端点 + 可选自定义请求头。
 * 请求头一般填 Authorization / Bearer，也可以放 X-API-Key 之类的私有鉴权。
 */
data class McpConfig(
    val serverUrl: String,
    val headerName: String = "",
    val headerValue: String = "",
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank()
}
