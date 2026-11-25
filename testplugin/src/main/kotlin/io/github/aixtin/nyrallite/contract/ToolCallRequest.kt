package io.github.aixtin.nyrallite.contract

/** 工具调用前事件（安全钩子 tool.before-call 的载体）。 */
data class ToolCallRequest(
    val pluginId: String,
    val tool: String,
    val arguments: String,
)
