package io.github.aixtin.nyrallite.contract

/**
 * 一次请求所需的最小上下文，不含任何厂商字段。
 * 厂商差异（端点/鉴权/参数）全部封装在传输插件内部。
 */
data class ChatRequest(
    val systemPrompt: String,
    val turns: List<ChatTurn>,
    /** 本轮可声明的工具；为空则不向模型暴露任何工具能力。 */
    val tools: List<ToolSpec> = emptyList(),
)

/**
 * 一条历史轮次：role 取 [ROLE_USER] / [ROLE_ASSISTANT] / [ROLE_TOOL]。
 *
 * - assistant 轮可携带 [toolCalls]，表示模型请求调用工具（content 可能为空）；
 * - tool 轮用 [toolCallId] 对应被调用的那一次，[content] 是执行结果。
 */
data class ChatTurn(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String = "",
    /** 仅 user 轮使用：随消息一起发送的图片等附件。 */
    val attachments: List<AttachmentRef> = emptyList(),
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_TOOL = "tool"
    }
}
