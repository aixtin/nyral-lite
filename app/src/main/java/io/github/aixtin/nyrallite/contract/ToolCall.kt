package io.github.aixtin.nyrallite.contract

/**
 * 模型请求的一次工具调用。
 * [arguments] 保持原始 JSON 字符串：可能是模型拼出的半成品，执行方解析失败要按业务错误回填。
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)
