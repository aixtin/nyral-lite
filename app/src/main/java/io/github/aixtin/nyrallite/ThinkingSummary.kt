package io.github.aixtin.nyrallite

/**
 * 思考区摘要行文案：
 *  - 结束态 [line]：首行有效文字 + 字数
 *  - 流式态 [tail]：最后一段的实时片段
 */
internal object ThinkingSummary {

    private const val SUMMARY_MAX = 22

    /** 流式片段最多显示的字符数。 */
    private const val SNIPPET_MAX = 20

    /** 流式片段非句末时的最小切换间隔（600ms），避免逐字刷新晃眼。 */
    const val SNIPPET_THROTTLE_NS = 600_000_000L

    private const val SENTENCE_END = "。！？；!?;…"

    /**
     * 取思考内容首行有效文字，去掉 Markdown 前缀符号，超长截断，末尾带字数。
     * 形如: 用户坚持"全看" …（128字）
     */
    fun line(thinking: String): String {
        val firstLine = thinking.lineSequence()
            .map { it.trim().trimStart('#', '>', '-', '*', ' ') }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        val text = if (firstLine.isEmpty()) "思考过程" else firstLine
        val clipped = if (text.length > SUMMARY_MAX) {
            text.take(SUMMARY_MAX).trimEnd() + "…"
        } else {
            text
        }
        return "$clipped（${thinking.length}字）"
    }

    /** 取最后一段思考，压成单行并截断到 [SNIPPET_MAX] 个字符；无有效内容返回空串。 */
    fun tail(thinking: String): String {
        val para = thinking.substringAfterLast("\n\n").replace('\n', ' ').trim()
        if (para.isEmpty()) return ""
        if (para.codePointCount(0, para.length) <= SNIPPET_MAX) return para
        return para.substring(0, para.offsetByCodePoints(0, SNIPPET_MAX)).trimEnd() + "…"
    }

    /** 是否该切换片段：内容确实变了，且句末立即切、其余按 [SNIPPET_THROTTLE_NS] 节流。 */
    fun shouldSwitch(thinking: String, shown: String, sinceNs: Long, nowNs: Long): Boolean {
        val next = tail(thinking)
        if (next.isEmpty() || next == shown) return false
        val end = thinking.lastOrNull() ?: return false
        return end in SENTENCE_END || nowNs - sinceNs >= SNIPPET_THROTTLE_NS
    }
}
