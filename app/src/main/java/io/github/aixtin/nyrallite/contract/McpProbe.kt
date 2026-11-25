package io.github.aixtin.nyrallite.contract

/**
 * MCP 连通性自检：按给定配置握手并拉取工具清单，不落盘、不影响正在使用的会话。
 * 配置页用它做"测试连接"。
 */
interface McpProbe {
    suspend fun probe(config: McpConfig): McpProbeResult
}

/** 自检结果：成功时 [error] 为 null，[tools] 为服务端声明的工具。 */
data class McpProbeResult(
    val tools: List<ToolSpec>,
    val error: String? = null,
)
