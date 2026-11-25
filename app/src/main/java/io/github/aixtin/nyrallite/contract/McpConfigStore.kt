package io.github.aixtin.nyrallite.contract

/**
 * MCP 服务器配置存取接口。
 * 实现由 storage-prefs 插件提供；配置页与 mcp-client 插件都只认这个接口。
 */
interface McpConfigStore {
    fun load(): McpConfig
    fun save(config: McpConfig)
}
