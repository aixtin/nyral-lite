package io.github.aixtin.nyrallite.contract

import java.io.File

/** 一条工具调用审计记录；[arguments] 在写入前已脱敏。 */
data class ToolAuditEntry(
    val pluginId: String,
    val tool: String,
    val arguments: String,
    /** allow / deny。 */
    val decision: String,
    /** 判定时的门禁档位 off / auto / strict。 */
    val mode: String,
    val reason: String,
)

/**
 * 工具调用审计日志：每次门禁判定追加一行 JSON（时间、工具、参数哈希与脱敏参数、决定、档位）。
 * 实现由 security-policy 插件提供，配置页可导出分享。
 */
interface ToolAuditLog {
    fun record(entry: ToolAuditEntry)

    /** 当前日志文件；尚未产生任何记录时为 null。 */
    fun file(): File?
}
