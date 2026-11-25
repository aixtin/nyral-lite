package io.github.aixtin.nyrallite.contract

/**
 * 工具调用卡片需要持久化的部分：名称、参数、结果与耗时。
 * 折叠状态是纯界面状态，不落盘。
 */
data class SessionToolCall(
    val name: String,
    val arguments: String,
    val result: String,
    val ok: Boolean,
    val durationMs: Long,
)

/**
 * 会话消息的持久化形态：只存渲染需要的最小字段，
 * 与界面模型 [io.github.aixtin.nyrallite.Message] 解耦，插件不依赖主体。
 */
data class SessionMessage(
    val role: String,
    val content: String,
    val thinking: String = "",
    /** role 为 "tool" 时的卡片数据。 */
    val tool: SessionToolCall? = null,
    /** 随消息发送的附件，只存引用，图片本体在文件里。 */
    val attachments: List<AttachmentRef> = emptyList(),
)

/** 历史列表用的会话摘要：不读消息正文，避免列表加载过重。 */
data class SessionSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    /** 会话绑定的人设；旧索引没有该字段时保持 null，不继承后来的默认。 */
    val personaId: String? = null,
)

/** 一次完整会话。 */
data class ChatSession(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<SessionMessage>,
    /** 正文也存一份，索引重建或缺失时仍能恢复绑定。 */
    val personaId: String? = null,
    /** 较早内容的滚动摘要；原文仍完整保存在 messages 里，可按需检索。 */
    val summary: String = "",
    /** 摘要已覆盖到的消息条数：低于它的部分只靠摘要和检索，不再逐条发送。 */
    val summaryUpTo: Int = 0,
)

/**
 * 会话持久化接口：列表、读取、保存、删除。
 * 实现由 storage-prefs 插件提供，主体只认这个契约。
 */
interface SessionStore {
    /** 按更新时间倒序列出会话摘要。 */
    fun list(): List<SessionSummary>

    /** 读取完整会话；文件缺失或损坏时返回 null。 */
    fun load(id: String): ChatSession?

    /** 覆盖保存：会话已存在则替换。 */
    fun save(session: ChatSession)

    fun delete(id: String)
}
