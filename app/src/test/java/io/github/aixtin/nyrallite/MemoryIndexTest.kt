package io.github.aixtin.nyrallite

import io.github.aixtin.nyrallite.contract.MemoryEntry
import io.github.aixtin.nyrallite.contract.MemoryStore
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryIndexTest {

    private fun store(entries: List<MemoryEntry>) = object : MemoryStore {
        override fun all() = entries
        override fun search(query: String, limit: Int) = entries.take(limit)
        override fun add(text: String, title: String, sessionId: String, pinned: Boolean) =
            error("目录测试不写入")

        override fun delete(id: Long) = false
        override fun setPinned(id: Long, pinned: Boolean) = false
        override fun count() = entries.size
    }

    @Test
    fun pinnedEntryStaysInIndexEvenWhenOld() {
        val now = 1_800_000_000_000L
        val old = MemoryEntry(id = 1, text = "旧", title = "置顶旧事", ts = 0, pinned = true)
        val recent = (2..25).map {
            MemoryEntry(id = it.toLong(), text = "新", title = "条目$it", ts = now - it * 1000L)
        }
        val block = MemoryIndex.block(store(listOf(old) + recent))
        val lines = block.lines().filter { it.startsWith("#") }

        assertTrue(lines.isNotEmpty())
        assertTrue(lines.first().contains("置顶旧事"))
    }
}
