package io.github.aixtin.nyrallite.plugins.attachmentstore

import io.github.aixtin.nyrallite.contract.AttachmentRef
import io.github.aixtin.nyrallite.contract.AttachmentStore
import java.io.File
import java.security.MessageDigest

/**
 * 私有目录存储：`filesDir/attachments/<sha256 前 32 位>.<ext>`。
 * 内容寻址天然去重；文件名就是 key，会话 JSON 只存引用。
 * 删会话不直接删文件，由启动时的 [prune] 按存活引用统一回收。
 */
class PrivateAttachmentStore(private val dir: File) : AttachmentStore {

    override val id: String = STORE_ID

    override fun put(bytes: ByteArray, mime: String, name: String): AttachmentRef {
        val key = "${sha256(bytes).take(HASH_CHARS)}.${extensionOf(mime)}"
        val target = File(dir, key)
        if (!target.isFile) {
            dir.mkdirs()
            val tmp = File(dir, "$key.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.writeBytes(bytes)
                tmp.delete()
            }
        }
        return AttachmentRef(id, key, mime, name, bytes.size.toLong())
    }

    override fun open(ref: AttachmentRef): ByteArray? {
        val file = fileOf(ref.key) ?: return null
        if (!file.isFile) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    override fun delete(ref: AttachmentRef) {
        fileOf(ref.key)?.delete()
    }

    /**
     * 启动 GC：删掉仍不在 [liveKeys] 里的文件（含上次中断留下的 .tmp）。
     * 只回收自己的目录，其他存储实现不参与自动删除；
     * 新写入的文件有 [GRACE_MS] 保护期，避免和刚发送的图片抢跑。
     */
    fun prune(liveKeys: Set<String>): Int {
        val files = dir.listFiles() ?: return 0
        val now = System.currentTimeMillis()
        var removed = 0
        for (file in files) {
            if (file.name in liveKeys) continue
            if (now - file.lastModified() < GRACE_MS) continue
            if (file.delete()) removed++
        }
        return removed
    }

    /** key 来自自己的 JSON，仍挡一下路径穿越，避免脏数据写出目录。 */
    private fun fileOf(key: String): File? {
        if (key.isEmpty() || key.contains('/') || key.contains("..")) return null
        return File(dir, key)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun extensionOf(mime: String): String = when (mime.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
    }

    companion object {
        const val STORE_ID = "private"
        private const val HASH_CHARS = 32
        private const val GRACE_MS = 10 * 60 * 1000L
    }
}
