package io.github.aixtin.nyrallite.plugins.storageprefs

import io.github.aixtin.nyrallite.contract.ChatSession
import io.github.aixtin.nyrallite.contract.SessionMessage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileSessionStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun storesPersonaInBodyAndIndex() {
        val store = FileSessionStore(tmp.root)
        store.save(
            ChatSession(
                id = "s1",
                title = "测试",
                updatedAt = 1L,
                messages = listOf(SessionMessage(role = "user", content = "你好")),
                personaId = "p1",
            ),
        )

        assertEquals("p1", store.load("s1")?.personaId)
        assertEquals("p1", store.list().single().personaId)
        val index = JSONArray(File(tmp.root, "index.json").readText()).getJSONObject(0)
        assertEquals("p1", index.getString("personaId"))
    }

    @Test
    fun legacySessionDoesNotInheritADefault() {
        File(tmp.root, "old.json").writeText(
            JSONObject()
                .put("id", "old")
                .put("title", "旧会话")
                .put("messages", JSONArray())
                .toString(),
        )

        val loaded = FileSessionStore(tmp.root).load("old")
        assertNull(loaded?.personaId)
        assertEquals("", loaded?.summary)
        assertEquals(0, loaded?.summaryUpTo)
    }

    @Test
    fun storesRollingSummaryWithWatermark() {
        val store = FileSessionStore(tmp.root)
        store.save(
            ChatSession(
                id = "s2",
                title = "摘要",
                updatedAt = 1L,
                messages = listOf(
                    SessionMessage(role = "user", content = "a"),
                    SessionMessage(role = "ai", content = "b"),
                ),
                summary = "- 用户在测试摘要",
                summaryUpTo = 2,
            ),
        )

        val loaded = FileSessionStore(tmp.root).load("s2")
        assertEquals("- 用户在测试摘要", loaded?.summary)
        assertEquals(2, loaded?.summaryUpTo)
    }
}
