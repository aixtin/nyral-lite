package io.github.aixtin.nyrallite

/** 思考区摘要行文案：首行有效文字 + 字数。 */
internal object ThinkingSummary {

    private const val SUMMARY_MAX = 22

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
}
