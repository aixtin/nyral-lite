package io.github.aixtin.nyrallite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryTextTest {

    @Test
    fun normalizeStripsBulletsAndFences() {
        val raw = "```\n1. 用户在做笔记应用\n* 决定用 Kotlin\n\n- 未完成：检索\n```"

        val text = SummaryText.normalize(raw)

        assertTrue(text.startsWith("- "))
        assertTrue(text.contains("用户在做笔记应用"))
        assertFalse(text.contains("```"))
        assertEquals(3, text.lines().size)
    }

    @Test
    fun emptySummaryDoesNotInjectABlock() {
        assertTrue(SummaryText.block("", 10).isEmpty())
        assertTrue(SummaryText.block("   ", 10).isEmpty())
    }

    @Test
    fun blockPointsModelAtHistorySearch() {
        val block = SummaryText.block("- 用户在写 Android 应用", 12)

        assertTrue(block.contains("12"))
        assertTrue(block.contains("history_search"))
    }
}
