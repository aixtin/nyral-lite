package io.github.aixtin.nyrallite.contract

/**
 * 附件引用：只记录"去哪读"，不携带字节。
 *
 * [store] 是实现 id（如 "private"），一起落盘，将来换存储实现时老会话仍能找回；
 * [key] 是实现内部的定位串（私有存储里是哈希文件名），[name] 只用于展示。
 */
data class AttachmentRef(
    val store: String,
    val key: String,
    val mime: String,
    val name: String,
    val size: Long = 0L,
)
