package io.github.aixtin.nyrallite.plugins.personastore

import io.github.aixtin.nyrallite.contract.PersonaCard
import io.github.aixtin.nyrallite.contract.PersonaMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class PersonaData(
    val cards: List<PersonaCard> = emptyList(),
    val defaultId: String? = null,
)

/** 人设库 JSON 序列化；损坏文件按空库读取，不阻断 App 启动。 */
internal object PersonaFile {

    fun load(file: File): PersonaData {
        if (!file.isFile) return PersonaData()
        return runCatching {
            val root = JSONObject(file.readText())
            val array = root.optJSONArray("cards") ?: JSONArray()
            val cards = (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                val name = o.optString("name")
                if (id.isBlank() || name.isBlank()) return@mapNotNull null
                PersonaCard(
                    id = id,
                    name = name,
                    systemPrompt = o.optString("systemPrompt"),
                    personality = o.optString("personality"),
                    scenario = o.optString("scenario"),
                    description = o.optString("description"),
                    exampleDialogue = o.optString("exampleDialogue"),
                    voiceContent = o.optString("voiceContent"),
                    firstMessage = o.optString("firstMessage"),
                    mode = runCatching {
                        PersonaMode.valueOf(o.optString("mode", "SHORT"))
                    }.getOrDefault(PersonaMode.SHORT),
                )
            }
            val default = root.optString("defaultId").ifBlank { null }
            PersonaData(cards, default?.takeIf { id -> cards.any { it.id == id } })
        }.getOrDefault(PersonaData())
    }

    fun save(file: File, data: PersonaData) {
        val array = JSONArray()
        data.cards.forEach { card ->
            array.put(
                JSONObject()
                    .put("id", card.id)
                    .put("name", card.name)
                    .put("systemPrompt", card.systemPrompt)
                    .put("personality", card.personality)
                    .put("scenario", card.scenario)
                    .put("description", card.description)
                    .put("exampleDialogue", card.exampleDialogue)
                    .put("voiceContent", card.voiceContent)
                    .put("firstMessage", card.firstMessage)
                    .put("mode", card.mode.name),
            )
        }
        val root = JSONObject().put("cards", array)
        data.defaultId?.let { root.put("defaultId", it) }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(root.toString())
            tmp.delete()
        }
    }
}
