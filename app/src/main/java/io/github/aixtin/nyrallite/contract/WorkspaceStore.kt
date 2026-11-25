package io.github.aixtin.nyrallite.contract

/**
 * 聊天工作区状态：当前会话与尚未发送的草稿。
 * 草稿不进入历史列表，也不属于模型上下文。
 */
interface WorkspaceStore {
    /** 当前会话 id；即使会话还没有消息也会保存。 */
    var currentSessionId: String?

    /** 空白会话尚未写入正文时，也要记住它当前绑定的人设。 */
    var currentPersonaId: String?

    fun loadDraft(sessionId: String): String

    /** 空白草稿等价于删除。 */
    fun saveDraft(sessionId: String, text: String)

    fun deleteDraft(sessionId: String)
}
