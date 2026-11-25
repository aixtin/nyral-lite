package io.github.aixtin.nyrallite.contract

/** 短人设常驻基础信息；长人设用角色描述、示例与互动要求整体替换它。 */
enum class PersonaMode { SHORT, LONG }

/** 一张可复用的人设卡；会话只保存 id，正文修改后所有绑定会话共享。 */
data class PersonaCard(
    val id: String,
    val name: String,
    val systemPrompt: String = "",
    val personality: String = "",
    val scenario: String = "",
    val description: String = "",
    val exampleDialogue: String = "",
    val voiceContent: String = "",
    val mode: PersonaMode = PersonaMode.SHORT,
)

/** 人设库与全局默认项；实现负责持久化和唯一默认约束。 */
interface PersonaStore {
    val defaultId: String?

    fun all(): List<PersonaCard>

    fun get(id: String): PersonaCard?

    fun upsert(card: PersonaCard)

    fun delete(id: String): Boolean

    /** null 表示全局不启用；已有会话的绑定不受此操作影响。 */
    fun setDefault(id: String?)

    fun setMode(id: String, mode: PersonaMode): Boolean
}
