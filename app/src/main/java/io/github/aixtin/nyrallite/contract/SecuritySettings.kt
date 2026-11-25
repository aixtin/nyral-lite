package io.github.aixtin.nyrallite.contract

/**
 * 工具门禁档位：
 * - [OFF]：全放行，等于关掉门禁；
 * - [AUTO]：禁止名单拦截，白名单直接放行，其余（MCP 动态工具一律视为高危）需用户确认；
 * - [STRICT]：白名单模式，名单之外一律拒绝，不弹确认。
 */
enum class SecurityMode(val raw: String) {
    OFF("off"),
    AUTO("auto"),
    STRICT("strict"),
    ;

    companion object {
        fun of(raw: String): SecurityMode =
            entries.firstOrNull { it.raw == raw.trim().lowercase() } ?: AUTO
    }
}

/** 安全门禁配置：档位 + 工具白名单/禁止名单（英文逗号或换行分隔）。 */
data class SecuritySettings(
    val mode: SecurityMode = SecurityMode.AUTO,
    val allowTools: String = "",
    val denyTools: String = "",
)

/**
 * 安全门禁配置存取接口。
 * 实现由 security-policy 插件提供（落在该插件自己的配置命名空间），配置页与门禁共用一份数据。
 */
interface SecuritySettingsStore {
    fun load(): SecuritySettings
    fun save(settings: SecuritySettings)
}
