package io.github.aixtin.nyrallite.plugins.security

import io.github.aixtin.nyrallite.contract.ToolAuditEntry
import io.github.aixtin.nyrallite.contract.ToolAuditLog
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/**
 * [ToolAuditLog] 的文件实现：每条记录一行 JSON，追加写入应用私有目录，
 * 参数先做哈希（前 12 位）与脱敏再落盘，写失败不影响工具调用本身。
 */
internal class FileToolAuditLog(private val file: File) : ToolAuditLog {

    private val lock = Any()

    override fun record(entry: ToolAuditEntry) {
        val line = JSONObject()
            .put("ts", Instant.now().toString())
            .put("pluginId", entry.pluginId)
            .put("tool", entry.tool)
            .put("argsHash", hash(entry.arguments))
            .put("args", AuditSanitizer.sanitize(entry.arguments).take(MAX_ARGS_CHARS))
            .put("decision", entry.decision)
            .put("mode", entry.mode)
            .put("reason", entry.reason)
            .toString()
        synchronized(lock) {
            runCatching {
                file.parentFile?.mkdirs()
                file.appendText(line + "\n")
            }
        }
    }

    override fun file(): File? = file.takeIf { it.isFile && it.length() > 0 }

    private fun hash(raw: String): String = MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        .take(HASH_CHARS)

    private companion object {
        /** 脱敏后参数的最大落盘长度，避免日志膨胀。 */
        const val MAX_ARGS_CHARS = 512

        /** 参数哈希保留位数。 */
        const val HASH_CHARS = 12
    }
}
