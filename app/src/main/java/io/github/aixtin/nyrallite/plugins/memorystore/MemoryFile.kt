package io.github.aixtin.nyrallite.plugins.memorystore

import io.github.aixtin.nyrallite.contract.MemoryEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 记忆文件的序列化：只做「JSON ⇄ 条目列表」。
 * 读写策略（何时存、淘汰谁）留在 FileMemoryStore，避免一个文件两头改。
 */
internal object MemoryFile {

    private const val KEY_ID = "id"
    private const val KEY_TEXT = "text"
    private const val KEY_TITLE = "title"
    private const val KEY_TS = "ts"
    private const val KEY_SESSION = "session"
    private const val KEY_PINNED = "pinned"

    /** 读不到或损坏时返回空列表，不阻断启动。 */
    fun load(file: File): List<MemoryEntry> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val text = o.optString(KEY_TEXT)
                if (text.isEmpty()) return@mapNotNull null
                MemoryEntry(
                    id = o.optLong(KEY_ID, 0L),
                    text = text,
                    title = o.optString(KEY_TITLE).ifEmpty { MemoryScoring.titleOf(text) },
                    ts = o.optLong(KEY_TS),
                    sessionId = o.optString(KEY_SESSION),
                    pinned = o.optBoolean(KEY_PINNED, false),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 先写临时文件再改名，进程被杀不会留下半截 JSON。 */
    fun save(file: File, entries: List<MemoryEntry>) {
        runCatching {
            file.parentFile?.mkdirs()
            val array = JSONArray()
            for (entry in entries) {
                array.put(
                    JSONObject()
                        .put(KEY_ID, entry.id)
                        .put(KEY_TEXT, entry.text)
                        .put(KEY_TITLE, entry.title)
                        .put(KEY_TS, entry.ts)
                        .put(KEY_SESSION, entry.sessionId)
                        .put(KEY_PINNED, entry.pinned),
                )
            }
            val text = array.toString()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }
    }
}
