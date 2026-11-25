package io.github.aixtin.nyrallite

import io.github.aixtin.nyrallite.contract.SessionMessage
import io.github.aixtin.nyrallite.contract.SessionToolCall

/** 界面消息 ↔ 持久化会话消息的转换，以及标题与可见性过滤。 */
internal object SessionMessages {

    /** 错误提示是临时状态；空 AI 占位、还没跑完的工具卡片都不进入历史。 */
    fun visible(messages: List<Message>): List<Message> = messages.filter { msg ->
        msg.role != "error" &&
            !(msg.role == "ai" && msg.content.isBlank() && msg.thinking.isBlank()) &&
            (msg.role != "tool" || msg.toolCard?.running == false)
    }

    fun toSessionMessage(msg: Message): SessionMessage = SessionMessage(
        role = msg.role,
        content = msg.content,
        thinking = msg.thinking,
        tool = msg.toolCard?.let {
            SessionToolCall(
                name = it.name,
                arguments = it.arguments,
                result = it.result,
                ok = it.ok,
                durationMs = it.durationMs,
            )
        },
        attachments = msg.attachments,
    )

    /** 持久化消息还原成界面消息；非法角色直接丢弃。 */
    fun toMessage(m: SessionMessage): Message? = when {
        m.role != "user" && m.role != "ai" && m.role != "tool" -> null
        m.role == "tool" -> m.tool?.let {
            Message(
                role = "tool",
                content = "",
                toolCard = ToolCard(
                    name = it.name,
                    arguments = it.arguments,
                    result = it.result,
                    ok = it.ok,
                    running = false,
                    durationMs = it.durationMs,
                ),
            )
        }
        m.role == "ai" && m.content.isBlank() && m.thinking.isBlank() -> null
        else -> Message(
            role = m.role,
            content = m.content,
            thinking = m.thinking,
            attachments = m.attachments,
        )
    }

    /** 标题取首条用户消息；纯图片消息没有正文，给一个可读的占位标题。 */
    fun titleOf(list: List<Message>): String {
        val first = list.firstOrNull { it.role == "user" && it.content.isNotBlank() }
            ?: return if (list.any { it.role == "user" && it.attachments.isNotEmpty() }) {
                IMAGE_TITLE
            } else {
                DEFAULT_TITLE
            }
        return first.content.trim()
            .replace(Regex("\\s+"), " ")
            .take(TITLE_CHARS)
    }

    private const val TITLE_CHARS = 24
    private const val DEFAULT_TITLE = "新会话"
    private const val IMAGE_TITLE = "图片"
}
