package io.github.aixtin.nyrallite.plugins.memorystore

import io.github.aixtin.nyrallite.contract.MemoryEntry
import io.github.aixtin.nyrallite.contract.MemoryStore
import java.io.File

/**
 * 记忆的文件实现：全量载入内存后按需检索，几百条量级用不上数据库。
 *
 * 上限 [MAX_ENTRIES] 条，超出时淘汰最旧的未置顶条目，
 * 让文件体积和启动解析时间都可预期。序列化细节见 [MemoryFile]。
 */
class FileMemoryStore(private val file: File) : MemoryStore {

    private val lock = Any()
    private var loaded = false
    private val entries = mutableListOf<MemoryEntry>()
    private var nextId = 1L

    override fun all(): List<MemoryEntry> = synchronized(lock) {
        ensureLoaded()
        entries.toList()
    }

    override fun search(query: String, limit: Int): List<MemoryEntry> = synchronized(lock) {
        ensureLoaded()
        if (entries.isEmpty() || limit <= 0) return emptyList()
        val normalized = query.trim()
        if (normalized.isEmpty()) {
            return entries.sortedByDescending { it.ts }.take(limit)
        }
        val grams = MemoryScoring.grams(normalized)
        if (grams.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        return entries.asSequence()
            .map { it to MemoryScoring.score(it, normalized, grams, now) }
            .filter { it.second >= MemoryScoring.MIN_SCORE }
            .sortedWith(
                compareByDescending<Pair<MemoryEntry, Double>> { it.second }
                    .thenByDescending { it.first.ts },
            )
            .take(limit)
            .map { it.first }
            .toList()
    }

    override fun add(
        text: String,
        title: String,
        sessionId: String,
        pinned: Boolean,
    ): MemoryEntry = synchronized(lock) {
        ensureLoaded()
        val entry = MemoryEntry(
            id = nextId++,
            text = text.trim(),
            title = title.trim().ifEmpty { MemoryScoring.titleOf(text) },
            ts = System.currentTimeMillis(),
            sessionId = sessionId,
            pinned = pinned,
        )
        entries += entry
        evictIfNeeded()
        MemoryFile.save(file, entries)
        entry
    }

    override fun delete(id: Long): Boolean = synchronized(lock) {
        ensureLoaded()
        val removed = entries.removeAll { it.id == id }
        if (removed) MemoryFile.save(file, entries)
        removed
    }

    override fun setPinned(id: Long, pinned: Boolean): Boolean = synchronized(lock) {
        ensureLoaded()
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0) return false
        val old = entries[index]
        if (old.pinned == pinned) return true
        entries[index] = old.copy(pinned = pinned)
        MemoryFile.save(file, entries)
        true
    }

    override fun count(): Int = synchronized(lock) {
        ensureLoaded()
        entries.size
    }

    /** 超限时按时间从旧到新淘汰未置顶条目。 */
    private fun evictIfNeeded() {
        if (entries.size <= MAX_ENTRIES) return
        val overflow = entries.size - MAX_ENTRIES
        val victims = entries.filterNot { it.pinned }
            .sortedBy { it.ts }
            .take(overflow)
            .map { it.id }
            .toSet()
        if (victims.isEmpty()) return
        entries.removeAll { it.id in victims }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        entries += MemoryFile.load(file)
        nextId = (entries.maxOfOrNull { it.id } ?: 0L) + 1
    }

    private companion object {
        const val MAX_ENTRIES = 500
    }
}
