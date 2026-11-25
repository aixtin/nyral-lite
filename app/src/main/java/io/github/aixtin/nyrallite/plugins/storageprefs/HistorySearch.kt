package io.github.aixtin.nyrallite.plugins.storageprefs

import io.github.aixtin.nyrallite.contract.SessionMessage

/**
 * 会话原文的本地关键词检索：不联网、不建索引，只在当前会话的完整消息里找。
 *
 * 排序先看命中词数，再看消息越新越靠前；返回的序号是会话内位置（从 1 开始），
 * 模型可以据此对照上下文或继续追问。
 */
internal object HistorySearch {

    data class Hit(val index: Int, val role: String, val content: String)

    fun rank(query: String, messages: List<SessionMessage>, limit: Int): List<Hit> {
        val terms = terms(query)
        if (terms.isEmpty()) return emptyList()
        return messages.asSequence()
            .mapIndexedNotNull { index, msg ->
                if (msg.role != "user" && msg.role != "ai") return@mapIndexedNotNull null
                if (msg.content.isBlank()) return@mapIndexedNotNull null
                val text = msg.content.lowercase()
                val score = terms.count { text.contains(it) }
                if (score == 0) null else Triple(index, score, msg)
            }
            .sortedWith(
                compareByDescending<Triple<Int, Int, SessionMessage>> { it.second }
                    .thenByDescending { it.first },
            )
            .take(limit)
            .map { Hit(it.first + 1, it.third.role, it.third.content) }
            .toList()
    }

    /** 按空白和标点切词；中文长词再拆相邻双字，避免整句当一个词而检索不到。 */
    private fun terms(query: String): List<String> {
        val parts = query.lowercase()
            .split(Regex("[\\s，。、；：！？,.;:!?\"'“”‘’()（）\\[\\]]+"))
            .filter { it.isNotBlank() }
        return parts.flatMap { part ->
            if (part.length <= 2 || part.all { it.code < 128 }) {
                listOf(part)
            } else {
                (0..part.length - 2).map { part.substring(it, it + 2) }
            }
        }.distinct()
    }
}
