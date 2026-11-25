package io.github.aixtin.nyrallite.plugins.personastore

import io.github.aixtin.nyrallite.contract.PersonaCard
import io.github.aixtin.nyrallite.contract.PersonaMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FilePersonaStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun persistsCardsModeAndSingleDefault() {
        val file = File(tmp.root, "personas.json")
        val store = FilePersonaStore(file)
        store.upsert(PersonaCard(id = "a", name = "甲"))
        store.upsert(PersonaCard(id = "b", name = "乙"))
        store.setDefault("a")
        store.setDefault("b")
        assertTrue(store.setMode("b", PersonaMode.LONG))

        val restored = FilePersonaStore(file)
        assertEquals("b", restored.defaultId)
        assertEquals(PersonaMode.LONG, restored.get("b")?.mode)
        assertEquals(2, restored.all().size)

        restored.delete("b")
        assertNull(restored.defaultId)
        assertEquals(listOf("a"), FilePersonaStore(file).all().map { it.id })
    }
}
