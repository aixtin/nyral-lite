package io.github.aixtin.nyrallite.plugins.personastore

import io.github.aixtin.nyrallite.contract.PersonaCard
import io.github.aixtin.nyrallite.contract.PersonaMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaPromptTest {

    private val card = PersonaCard(
        id = "1",
        name = "测试",
        systemPrompt = "短指令",
        personality = "短性格",
        scenario = "短场景",
        description = "长描述",
        exampleDialogue = "长示例",
        voiceContent = "长互动",
    )

    @Test
    fun shortModeOnlyContainsShortFields() {
        val block = PersonaPrompt.block(card)

        assertTrue(block.contains("短指令"))
        assertTrue(block.contains("短性格"))
        assertTrue(block.contains("短场景"))
        assertFalse(block.contains("长描述"))
        assertFalse(block.contains("长示例"))
        assertFalse(block.contains("长互动"))
    }

    @Test
    fun longModeReplacesInsteadOfStackingShortFields() {
        val block = PersonaPrompt.block(card.copy(mode = PersonaMode.LONG))

        assertTrue(block.contains("长描述"))
        assertTrue(block.contains("长示例"))
        assertTrue(block.contains("长互动"))
        assertFalse(block.contains("短指令"))
        assertFalse(block.contains("短性格"))
        assertFalse(block.contains("短场景"))
    }

    @Test
    fun emptyModeDoesNotInjectAHeader() {
        assertTrue(PersonaPrompt.block(PersonaCard(id = "1", name = "空")).isEmpty())
    }
}
