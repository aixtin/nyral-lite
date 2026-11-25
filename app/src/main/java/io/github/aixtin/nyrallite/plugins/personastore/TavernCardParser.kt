package io.github.aixtin.nyrallite.plugins.personastore

import io.github.aixtin.nyrallite.contract.PersonaCard
import org.json.JSONObject
import java.util.UUID

/** SillyTavern chara_card_v2 解析器，同时读取 Operit 的语音扩展。 */
object TavernCardParser {

    fun parse(text: String, id: String = UUID.randomUUID().toString()): PersonaCard {
        val root = JSONObject(text)
        val spec = root.optString("spec")
        require(spec.isBlank() || spec == "chara_card_v2") { "仅支持 chara_card_v2 角色卡" }
        val data = root.optJSONObject("data") ?: root
        val operit = data.optJSONObject("extensions")
            ?.optJSONObject("operit")
            ?.optJSONObject("character_card")
        val name = clean(data.optString("name"))
            .ifEmpty { clean(operit?.optString("name").orEmpty()) }
            .ifEmpty { "未命名人设" }
        return PersonaCard(
            id = id,
            name = name,
            systemPrompt = clean(data.optString("system_prompt")),
            personality = clean(data.optString("personality")),
            scenario = clean(data.optString("scenario")),
            description = clean(data.optString("description")),
            exampleDialogue = clean(data.optString("mes_example")),
            voiceContent = clean(operit?.optString("otherContentVoice").orEmpty()),
        )
    }

    /** 空字段和导出器自带的占位文字都不进入提示词。 */
    private fun clean(value: String): String {
        val trimmed = value.trim()
        return if (trimmed in PLACEHOLDERS) "" else trimmed
    }

    private val PLACEHOLDERS = setOf("系统默认的角色卡配置", "开场白")
}
