package io.github.aixtin.nyrallite

/** 会话运行时可变量：切换、摘要与落盘都会改写这里，供 [SessionOpener] 与控制器共享。 */
internal class SessionRuntime {
    /** 当前会话 id：新建会话也立即分配，草稿才能在首次发送前落盘。 */
    var currentId: String? = null

    /** 人设只跟当前会话走；全局默认仅在新开会话时读取。 */
    var personaId: String? = null

    /** 较早内容的滚动摘要；原文仍完整落盘，模型需要时用 history_search 取。 */
    var summaryText: String = ""
    var summarizedUpTo: Int = 0

    /** 当前 id 是否已有会话正文文件；纯新会话/纯草稿会话不查历史列表。 */
    var persisted: Boolean = false
}
