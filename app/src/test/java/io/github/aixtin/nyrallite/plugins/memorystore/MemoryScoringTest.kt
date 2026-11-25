package io.github.aixtin.nyrallite.plugins.memorystore

import io.github.aixtin.nyrallite.contract.MemoryEntry
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryScoringTest {

    private val now = 1_800_000_000_000L

    private fun entry(text: String, title: String = "", ageDays: Long = 0) = MemoryEntry(
        id = 1,
        text = text,
        title = title,
        ts = now - ageDays * 86_400_000L,
    )

    @Test
    fun chineseQuerySplitsIntoBigrams() {
        val grams = MemoryScoring.grams("门禁配置")

        assertTrue(grams.contains("门禁"))
        assertTrue(grams.contains("禁配"))
    }

    @Test
    fun asciiQueryKeepsWholeWord() {
        val grams = MemoryScoring.grams("MCP server")

        assertTrue(grams.contains("mcp"))
        assertTrue(grams.contains("server"))
    }

    @Test
    fun relatedEntryScoresAboveThreshold() {
        val grams = MemoryScoring.grams("门禁")
        val score = MemoryScoring.score(entry("门禁当前是放行档"), "门禁", grams, now)

        assertTrue(score >= MemoryScoring.MIN_SCORE)
    }

    @Test
    fun unrelatedEntryIsFiltered() {
        val grams = MemoryScoring.grams("天气")
        val score = MemoryScoring.score(entry("喜欢喝美式咖啡"), "天气", grams, now)

        assertTrue(score < MemoryScoring.MIN_SCORE)
    }

    @Test
    fun exactPhraseBeatsSingleBigram() {
        val grams = MemoryScoring.grams("门禁放行")
        val exact = MemoryScoring.score(entry("门禁放行"), "门禁放行", grams, now)
        val partial = MemoryScoring.score(entry("门禁是严格档"), "门禁放行", grams, now)

        assertTrue(exact > partial)
    }

    @Test
    fun oldEntryLosesTiesAgainstRecentOne() {
        val grams = MemoryScoring.grams("项目")
        val fresh = MemoryScoring.score(entry("项目叫 Nyral Lite"), "项目", grams, now)
        val stale = MemoryScoring.score(entry("项目叫 Nyral Lite", ageDays = 365), "项目", grams, now)

        assertTrue(fresh > stale)
    }
}
