package io.github.aixtin.nyrallite

import io.github.aixtin.nyrallite.contract.ChatTurn

/**
 * 从落盘消息重建请求历史。
 * 工具协议轮不持久化，因此恢复时把同一用户问题下的多段 AI 正文合并回一份。
 */
internal object HistoryRebuilder {

    fun rebuild(loaded: List<Message>): MutableList<ChatTurn> {
        val history = mutableListOf<ChatTurn>()
        val pendingAssistant = StringBuilder()
        fun flush() {
            if (pendingAssistant.isBlank()) return
            history.add(ChatTurn("assistant", pendingAssistant.toString()))
            pendingAssistant.setLength(0)
        }
        loaded.forEach { msg ->
            when (msg.role) {
                "user" -> {
                    flush()
                    history.add(ChatTurn("user", msg.content, attachments = msg.attachments))
                }
                "ai" -> if (msg.content.isNotBlank()) {
                    if (pendingAssistant.isNotEmpty()) pendingAssistant.append("\n\n")
                    pendingAssistant.append(msg.content)
                }
            }
        }
        flush()
        return history
    }
}
