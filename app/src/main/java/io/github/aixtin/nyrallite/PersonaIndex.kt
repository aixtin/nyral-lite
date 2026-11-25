package io.github.aixtin.nyrallite

import io.github.aixtin.nyrallite.contract.PersonaStore
import io.github.aixtin.nyrallite.kernel.getOrNull
import io.github.aixtin.nyrallite.plugins.personastore.PersonaPrompt

/** 按会话绑定的 id 取人设；无绑定、卡被删或插件失败都不注入。 */
internal object PersonaIndex {

    fun blockFor(app: App, personaId: String?): String {
        if (personaId == null) return ""
        val store = app.kernel.services.getOrNull<PersonaStore>() ?: return ""
        val card = runCatching { store.get(personaId) }.getOrNull() ?: return ""
        return PersonaPrompt.block(card)
    }
}
