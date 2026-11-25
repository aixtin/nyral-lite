package io.github.aixtin.nyrallite

import io.github.aixtin.nyrallite.contract.PersonaStore
import io.github.aixtin.nyrallite.kernel.getOrNull
import io.github.aixtin.nyrallite.plugins.personastore.PersonaPrompt

/** 人设开场白：取卡片 firstMessage 并替换占位符；无卡片或无开场白时为空串。 */
internal object PersonaGreeting {

    fun of(app: App, personaId: String?): String {
        if (personaId == null) return ""
        val card = app.kernel.services.getOrNull<PersonaStore>()?.get(personaId) ?: return ""
        if (card.firstMessage.isBlank()) return ""
        return PersonaPrompt.fill(card.firstMessage, card)
    }
}
