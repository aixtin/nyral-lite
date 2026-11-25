package io.github.aixtin.nyrallite.plugins.personastore

import io.github.aixtin.nyrallite.contract.PersonaMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TavernCardParserTest {

    @Test
    fun parsesV2AndOperitVoiceContent() {
        val card = TavernCardParser.parse(SAMPLE, id = "card-1")

        assertEquals("card-1", card.id)
        assertEquals("萌主", card.name)
        assertEquals("你是 Operit", card.systemPrompt)
        assertEquals("安静", card.personality)
        assertEquals("未来", card.scenario)
        assertEquals("长描述", card.description)
        assertEquals("用户：你好\n萌主：你好", card.exampleDialogue)
        assertEquals("每句话尽量短", card.voiceContent)
    }

    @Test
    fun dropsEmptyAndPlaceholderFields() {
        val card = TavernCardParser.parse(
            SAMPLE.replace("长描述", "系统默认的角色卡配置")
                .replace("安静", "  "),
            id = "card-2",
        )

        assertTrue(card.description.isEmpty())
        assertTrue(card.personality.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherCardSpecifications() {
        TavernCardParser.parse(SAMPLE.replace("chara_card_v2", "chara_card_v3"))
    }

    @Test
    fun descriptionOnlyCardDefaultsToLongMode() {
        val json = """
            {
              "spec":"chara_card_v2",
              "data":{
                "name":"Fuka",
                "system_prompt":"",
                "personality":"",
                "scenario":"",
                "description":"她很倒霉"
              }
            }
        """.trimIndent()

        val card = TavernCardParser.parse(json)

        assertEquals(PersonaMode.LONG, card.mode)
    }

    private companion object {
        val SAMPLE = """
            {
              "spec":"chara_card_v2",
              "data":{
                "name":"萌主",
                "system_prompt":"你是 Operit",
                "personality":"安静",
                "scenario":"未来",
                "description":"长描述",
                "mes_example":"用户：你好\n萌主：你好",
                "extensions":{"operit":{"character_card":{
                  "otherContentVoice":"每句话尽量短"
                }}}
              }
            }
        """.trimIndent()
    }
}
