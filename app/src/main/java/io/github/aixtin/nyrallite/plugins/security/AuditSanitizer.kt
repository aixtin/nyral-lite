package io.github.aixtin.nyrallite.plugins.security

/**
 * 审计日志参数脱敏：隐藏私钥、常见密钥字段、Bearer 令牌、IP 与超长串。
 * 只用于落盘展示，不参与门禁判定。
 */
internal object AuditSanitizer {

    fun sanitize(raw: String): String = raw
        .replace(PRIVATE_KEY_BLOCK, "[私钥已隐藏]")
        .replace(BEARER_TOKEN, "Bearer ***")
        .replace(SECRET_FIELD) { "${it.groupValues[1]}***" }
        .replace(IPV4, "[IP]")
        .replace(LONG_TOKEN, "[已隐藏]")

    private val PRIVATE_KEY_BLOCK =
        Regex("-----BEGIN[^-]*PRIVATE KEY-----[\\s\\S]*?-----END[^-]*PRIVATE KEY-----")
    private val BEARER_TOKEN = Regex("(?i)bearer\\s+[A-Za-z0-9._\\-]+")
    private val SECRET_FIELD = Regex(
        "(?i)((?:token|password|passwd|secret|api[_-]?key|access[_-]?key|authorization)" +
            "[\"']?\\s*[:=]\\s*[\"']?)([^\"',}\\s]+)",
    )
    private val IPV4 = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")

    /** ≥16 位的长串（多半是密钥/哈希）整体隐藏。 */
    private val LONG_TOKEN = Regex("[A-Za-z0-9_\\-]{16,}")
}
