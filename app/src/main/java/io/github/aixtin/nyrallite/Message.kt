package io.github.aixtin.nyrallite

import io.github.aixtin.nyrallite.contract.AttachmentRef

/**
 * 一条聊天消息。极简模型：不做分段、不做块缓存、不做协议前缀剥离。
 * content 就是模型原文，渲染时交给唯一的 Markdown 引擎。
 */
data class Message(
    var role: String,   // "user" | "ai" | "error" | "tool"
    var content: String,
    var thinking: String = "",     // 思考过程(reasoning_content), 空表示无思考
    var thinkingCollapsed: Boolean = true,   // 默认折叠: 结束只留一行摘要, 点击才展开抽屉
    var streaming: Boolean = false,
    /** role 为 "tool" 时携带的工具调用卡片。 */
    var toolCard: ToolCard? = null,
    /** role 为 "user" 时随消息发送的图片等附件。 */
    var attachments: List<AttachmentRef> = emptyList(),
)
