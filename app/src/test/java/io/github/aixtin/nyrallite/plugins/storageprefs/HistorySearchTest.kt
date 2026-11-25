package io.github.aixtin.nyrallite.plugins.storageprefs

import io.github.aixtin.nyrallite.contract.SessionMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySearchTest {

    @Test
    fun findsChineseByAdjacentBigrams() {
        val messages = listOf(
            SessionMessage("user", "我们来聊聊项目名字"),
            SessionMessage("ai", "叫 Nyral Lite"),
        )

        val hits = HistorySearch.rank("项目名字", messages, 3)

        assertEquals(listOf(1), hits.map { it.index })
    }

    @Test
    fun ignoresToolMessagesAndBlankContent() {
        val messages = listOf(
            SessionMessage("tool", "项目名字"),
            SessionMessage("ai", "   "),
        )

        assertTrue(HistorySearch.rank("项目", messages, 3).isEmpty())
    }

    @Test
    fun equallyRelevantHitsPreferNewerMessages() {
        val messages = listOf(
            SessionMessage("user", "关键词"),
            SessionMessage("ai", "关键词"),
        )

        assertEquals(listOf(2, 1), HistorySearch.rank("关键词", messages, 3).map { it.index })
    }

    @Test
    fun englishWordMatchesAsWholeTerm() {
        val messages = listOf(
            SessionMessage("user", "把 persona 绑定到会话"),
            SessionMessage("ai", "收到"),
        )

        assertEquals(listOf(1), HistorySearch.rank("persona", messages, 3).map { it.index })
    }
}
