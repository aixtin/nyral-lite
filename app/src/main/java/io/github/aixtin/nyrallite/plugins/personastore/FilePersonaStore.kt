package io.github.aixtin.nyrallite.plugins.personastore

import io.github.aixtin.nyrallite.contract.PersonaCard
import io.github.aixtin.nyrallite.contract.PersonaMode
import io.github.aixtin.nyrallite.contract.PersonaStore
import java.io.File

/** 小体量人设库：内存缓存 + 单文件原子写入，所有操作串行。 */
class FilePersonaStore(private val file: File) : PersonaStore {

    private val lock = Any()
    private var data: PersonaData? = null

    override val defaultId: String?
        get() = synchronized(lock) { current().defaultId }

    override fun all(): List<PersonaCard> = synchronized(lock) { current().cards }

    override fun get(id: String): PersonaCard? = synchronized(lock) {
        current().cards.firstOrNull { it.id == id }
    }

    override fun upsert(card: PersonaCard) = synchronized(lock) {
        require(card.id.isNotBlank() && card.name.isNotBlank()) { "人设 id 与名称不能为空" }
        val old = current()
        val cards = old.cards.filterNot { it.id == card.id } + card
        update(old.copy(cards = cards))
    }

    override fun delete(id: String): Boolean = synchronized(lock) {
        val old = current()
        if (old.cards.none { it.id == id }) return false
        update(
            old.copy(
                cards = old.cards.filterNot { it.id == id },
                defaultId = old.defaultId?.takeIf { it != id },
            ),
        )
        true
    }

    override fun setDefault(id: String?) = synchronized(lock) {
        val old = current()
        require(id == null || old.cards.any { it.id == id }) { "人设不存在" }
        update(old.copy(defaultId = id))
    }

    override fun setMode(id: String, mode: PersonaMode): Boolean = synchronized(lock) {
        val old = current()
        val card = old.cards.firstOrNull { it.id == id } ?: return false
        update(old.copy(cards = old.cards.map { if (it.id == id) card.copy(mode = mode) else it }))
        true
    }

    private fun current(): PersonaData {
        data?.let { return it }
        return PersonaFile.load(file).also { data = it }
    }

    private fun update(value: PersonaData) {
        PersonaFile.save(file, value)
        data = value
    }
}
