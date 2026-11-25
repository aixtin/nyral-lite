package io.github.aixtin.nyrallite.plugins.storageprefs

import io.github.aixtin.nyrallite.contract.SessionSummary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** index.json 的读写：历史列表只读索引，不必解析全部会话正文。 */
internal object SessionIndex {

    fun read(file: File): List<SessionSummary> = runCatching {
        if (!file.isFile) return emptyList()
        val array = JSONArray(file.readText())
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            SessionSummary(
                id = o.optString(SessionKeys.ID),
                title = o.optString(SessionKeys.TITLE),
                updatedAt = o.optLong(SessionKeys.UPDATED_AT),
                personaId = o.optString(SessionKeys.PERSONA_ID).ifBlank { null },
            )
        }
    }.getOrDefault(emptyList())

    fun write(file: File, items: List<SessionSummary>) {
        val array = JSONArray()
        items.sortedByDescending { it.updatedAt }.forEach { s ->
            array.put(
                JSONObject()
                    .put(SessionKeys.ID, s.id)
                    .put(SessionKeys.TITLE, s.title)
                    .put(SessionKeys.UPDATED_AT, s.updatedAt)
                    .put(SessionKeys.PERSONA_ID, s.personaId),
            )
        }
        SessionJson.writeAtomically(file, array.toString())
    }
}
