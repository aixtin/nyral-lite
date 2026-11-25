package io.github.aixtin.nyrallite

import io.github.aixtin.nyrallite.contract.AttachmentRef
import io.github.aixtin.nyrallite.contract.ChatTurn
import io.github.aixtin.nyrallite.contract.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowTest {

    @Test
    fun shortHistoryStaysUntouched() {
        val history = listOf(
            ChatTurn("user", "你好"),
            ChatTurn("assistant", "你好，有什么可以帮你？"),
        )

        val result = ContextWindow.fit(history)

        assertEquals(history, result.turns)
        assertFalse(result.adjusted)
    }

    @Test
    fun dropsOldRoundAtUserBoundary() {
        val history = listOf(
            ChatTurn("user", "旧".repeat(25_000)),
            ChatTurn("assistant", "", toolCalls = listOf(ToolCall("old", "search", "{}"))),
            ChatTurn("tool", "旧结果", toolCallId = "old"),
            ChatTurn("assistant", "旧回答"),
            ChatTurn("user", "最新问题"),
        )

        val result = ContextWindow.fit(history)

        assertEquals(listOf(ChatTurn("user", "最新问题")), result.turns)
        assertEquals(4, result.droppedTurns)
    }

    @Test
    fun truncatesToolResultWithoutMutatingHistory() {
        val longResult = "结".repeat(5_000)
        val history = listOf(
            ChatTurn("user", "查一下"),
            ChatTurn("assistant", "", toolCalls = listOf(ToolCall("c1", "search", "{}"))),
            ChatTurn("tool", longResult, toolCallId = "c1"),
        )

        val result = ContextWindow.fit(history)

        assertEquals(5_000, history[2].content.length)
        assertTrue(result.turns[2].content.length < longResult.length)
        assertTrue(result.turns[2].content.endsWith("（工具结果过长，已截断）"))
        assertEquals(1, result.truncatedResults)
    }

    @Test
    fun removesOldestToolExchangeAsAWhole() {
        val history = mutableListOf(ChatTurn("user", "连续查询"))
        repeat(7) { index ->
            history += ChatTurn(
                "assistant",
                "",
                toolCalls = listOf(ToolCall("c$index", "search", "{}")),
            )
            history += ChatTurn("tool", "结果".repeat(2_000), toolCallId = "c$index")
        }

        val result = ContextWindow.fit(history)

        assertEquals("user", result.turns.first().role)
        assertTrue(result.droppedTurns > 0)
        result.turns.forEachIndexed { index, turn ->
            if (turn.role == "tool") {
                val previous = result.turns[index - 1]
                assertEquals("assistant", previous.role)
                assertTrue(previous.toolCalls.any { it.id == turn.toolCallId })
            }
        }
    }

    @Test
    fun neverTruncatesCurrentUserMessage() {
        val history = listOf(ChatTurn("user", "新".repeat(30_000)))

        val result = ContextWindow.fit(history)

        assertEquals(history, result.turns)
        assertEquals(0, result.droppedTurns)
    }

    @Test
    fun customBudgetDropsEarlierRounds() {
        val history = listOf(
            ChatTurn("user", "早".repeat(3_000)),
            ChatTurn("assistant", "早答"),
            ChatTurn("user", "新问题"),
        )

        val result = ContextWindow.fit(history, maxTokens = 2_000)

        assertEquals(listOf(ChatTurn("user", "新问题")), result.turns)
        assertEquals(2, result.droppedTurns)
    }

    @Test
    fun keepsOnlyLatestImageTurn() {
        val ref = AttachmentRef("private", "a.jpg", "image/jpeg", "a.jpg", 10)
        val history = listOf(
            ChatTurn("user", "第一张图", attachments = listOf(ref)),
            ChatTurn("assistant", "看到了"),
            ChatTurn("user", "第二张图", attachments = listOf(ref, ref)),
            ChatTurn("assistant", "又看到了"),
        )

        val result = ContextWindow.fit(history)

        assertTrue(result.turns[0].attachments.isEmpty())
        assertTrue(result.turns[0].content.endsWith("（图片已于早前发送）"))
        assertEquals(2, result.turns[2].attachments.size)
        // 原始历史不能被改写，重发/重试还要用到
        assertEquals(1, history[0].attachments.size)
    }
}
