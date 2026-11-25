package io.github.aixtin.nyrallite.contract

/**
 * 一条长期记忆：只存结论性内容（偏好、决定、常用信息），不存整段对话。
 * [title] 是常驻注入的索引行，[text] 是检索命中后才回给模型的正文。
 */
data class MemoryEntry(
    val id: Long,
    val text: String,
    val title: String,
    val ts: Long,
    val sessionId: String = "",
    val pinned: Boolean = false,
)

/**
 * 记忆读写接口。实现自己决定索引、打分与淘汰策略，主体只认这个契约。
 */
interface MemoryStore {
    /** 全量条目，按写入顺序；索引页与统计用。 */
    fun all(): List<MemoryEntry>

    /** 按相关度返回正文；[query] 为空时按时间倒序。 */
    fun search(query: String, limit: Int = 5): List<MemoryEntry>

    fun add(
        text: String,
        title: String = "",
        sessionId: String = "",
        pinned: Boolean = false,
    ): MemoryEntry

    fun delete(id: Long): Boolean

    /** 切换置顶；置顶条目不会因超限被淘汰，检索无命中时也会兜底返回。 */
    fun setPinned(id: Long, pinned: Boolean): Boolean

    fun count(): Int
}
