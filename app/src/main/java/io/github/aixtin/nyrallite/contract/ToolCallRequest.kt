package io.github.aixtin.nyrallite.contract

/**
 * 工具调用前事件（安全钩子 tool.before-call 的载体）。
 * 工具执行方在真正调用前交给 GateBus.check，安全插件可返回 Block 拦截。
 * 当前无工具调用方，接入远程 MCP 时启用。
 */
data class ToolCallRequest(
    val pluginId: String,
    val tool: String,
    val arguments: String,
)
