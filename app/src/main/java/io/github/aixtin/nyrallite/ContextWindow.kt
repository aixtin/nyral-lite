package io.github.aixtin.nyrallite

import io.github.aixtin.nyrallite.contract.ChatTurn

/**
 * 发送给模型的上下文窗口。
 *
 * 只生成一份精简副本，不修改控制器里的完整历史，也不影响界面与本地会话。
 * 裁剪以 user 消息为边界，避免留下没有 assistant(tool_calls) 的孤立 tool 结果。
 */
object ContextWindow {

    data class Result(
        val turns: List<ChatTurn>,
        val droppedTurns: Int,
        val truncatedResults: Int,
    ) {
        val adjusted: Boolean get() = droppedTurns > 0 || truncatedResults > 0
    }

    fun fit(history: List<ChatTurn>, maxTokens: Int = DEFAULT_MAX_TOKENS): Result {
        var truncated = 0
        val capped = history.map { turn ->
            if (turn.role != "tool" || turn.content.length <= MAX_TOOL_RESULT_CHARS) {
                turn
            } else {
                truncated++
                turn.copy(
                    content = turn.content.take(MAX_TOOL_RESULT_CHARS) + TOOL_TRUNCATED_MARK,
                )
            }
        }

        // 接口无状态，每轮都会重传全部历史：只保留最近一个带附件的 user 轮，
        // 更早的图片换成文字占位，否则第三轮起延迟和费用会翻倍。
        val lastWithAttachments = capped.indexOfLast {
            it.role == "user" && it.attachments.isNotEmpty()
        }
        val turns = if (lastWithAttachments < 0) {
            capped
        } else {
            capped.mapIndexed { index, turn ->
                if (index < lastWithAttachments && turn.attachments.isNotEmpty()) {
                    turn.copy(
                        content = turn.content + ATTACHMENT_ELIDED_MARK,
                        attachments = emptyList(),
                    )
                } else {
                    turn
                }
            }
        }

        val userStarts = turns.indices.filter { turns[it].role == "user" }
        var start = userStarts.firstOrNull() ?: 0
        var nextUser = 1
        // 每次丢弃一个完整的早期问答块；最后一个 user 块始终保留
        while (estimate(turns, start) > maxTokens && nextUser < userStarts.size) {
            start = userStarts[nextUser++]
        }
        val kept = turns.subList(start, turns.size).toMutableList()
        var dropped = start

        // 当前问答块仍过大时，从最老的工具轮开始成组移除，绝不只删 tool 结果
        while (estimate(kept, 0) > maxTokens) {
            val callIndex = kept.indexOfFirst { it.role == "assistant" && it.toolCalls.isNotEmpty() }
            if (callIndex < 0) break
            var end = callIndex + 1
            while (end < kept.size && kept[end].role == "tool") end++
            dropped += end - callIndex
            kept.subList(callIndex, end).clear()
        }

        // 单个新问题本身超预算时保留原文，把长度错误交给服务端明确反馈
        return Result(kept, dropped, truncated)
    }

    /** 粗估 token：ASCII 约 4 字符一个，其他字符保守按一个；另计消息、工具参数与图片开销。 */
    private fun estimate(turns: List<ChatTurn>, start: Int): Int {
        var total = 0
        for (i in start until turns.size) {
            val turn = turns[i]
            total += 4 + estimate(turn.content)
            total += turn.attachments.size * IMAGE_TOKENS
            turn.toolCalls.forEach { total += estimate(it.name) + estimate(it.arguments) }
        }
        return total
    }

    private fun estimate(text: String): Int {
        var ascii = 0
        var other = 0
        text.forEach { if (it.code <= 0x7f) ascii++ else other++ }
        return (ascii + 3) / 4 + other
    }

    /** 给 system、工具声明和输出预留空间的保守历史预算；用户可在配置页覆盖。 */
    const val DEFAULT_MAX_TOKENS = 24_000
    private const val MAX_TOOL_RESULT_CHARS = 4_000
    private const val TOOL_TRUNCATED_MARK = "\n…（工具结果过长，已截断）"

    /** 每张图按固定开销估算，避免 1568px 图片的实际计费被低估。 */
    private const val IMAGE_TOKENS = 1_200
    private const val ATTACHMENT_ELIDED_MARK = "\n\n（图片已于早前发送）"
}
