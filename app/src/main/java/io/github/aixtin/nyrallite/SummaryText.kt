package io.github.aixtin.nyrallite

/**
 * 前情摘要的成文与注入格式。
 *
 * 摘要只负责「知道有这么件事」，具体措辞、数字、名单一律交给 history_search
 * 回读原文；所以这里刻意保持短小，避免摘要自己膨胀成第二份历史。
 */
internal object SummaryText {

    private const val MAX_CHARS = 300
    private const val MAX_LINES = 6

    /** 把模型输出整理成整齐的要点：去代码块与空行，统一「- 」前缀，限行限字。 */
    fun normalize(raw: String): String {
        val lines = raw.lines()
            .map { it.trim().trimStart('-', '*', '•', '#').trim() }
            .filter { it.isNotEmpty() && !it.startsWith("```") }
            .take(MAX_LINES)
        val text = lines.joinToString("\n") { "- $it" }
        if (text.length <= MAX_CHARS) return text
        return text.take(MAX_CHARS).substringBeforeLast('\n')
    }

    /** 注入块；summarized 为已被摘要覆盖的消息条数。 */
    fun block(summary: String, summarized: Int): String {
        val body = summary.trim()
        if (body.isEmpty()) return ""
        return buildString {
            append("\n\n[前情摘要] 本会话较早的 ").append(summarized).append(" 条消息已压缩为以下要点：\n")
            append(body).append('\n')
            append("(摘要只记要点；涉及具体措辞、数字、名单时必须调用 history_search 检索原文，不要凭摘要编造)\n")
        }
    }
}
