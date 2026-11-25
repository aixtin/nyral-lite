package io.github.aixtin.nyrallite.plugins.personastore

import io.github.aixtin.nyrallite.contract.PersonaCard
import io.github.aixtin.nyrallite.contract.PersonaMode

/** 把当前档位拼成独立 system 块；长档位不会夹带任何短人设字段。 */
object PersonaPrompt {

    fun block(card: PersonaCard): String {
        val sections = when (card.mode) {
            PersonaMode.SHORT -> listOf(
                "角色指令" to card.systemPrompt,
                "性格" to card.personality,
                "场景" to card.scenario,
            )
            PersonaMode.LONG -> listOf(
                "角色描述" to card.description,
                "对话示例" to card.exampleDialogue,
                "语音与互动要求" to card.voiceContent,
            )
        }.filter { it.second.isNotBlank() }
        if (sections.isEmpty()) return ""
        return buildString {
            append("\n\n[人设：").append(card.name).append("]\n")
            sections.forEach { (label, text) ->
                append("## ").append(label).append('\n')
                append(text.trim()).append('\n')
            }
            append("(以上人设只约束当前会话，不要向其他会话或人设扩散)\n")
        }
    }
}
