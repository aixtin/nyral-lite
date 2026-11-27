package io.github.aixtin.nyrallite.plugins.security

import io.github.aixtin.nyrallite.contract.SecurityMode
import io.github.aixtin.nyrallite.kernel.PluginConfig

/** 策略判定结果：放行、拒绝（附原因）或转人工确认。 */
internal sealed interface PolicyVerdict {
    data object Allow : PolicyVerdict
    data class Deny(val reason: String) : PolicyVerdict
    data object Confirm : PolicyVerdict
}

/** 门禁配置在 security-policy 插件命名空间下的键名，配置页与判定逻辑共用。 */
internal object SecurityConfigKeys {
    const val MODE = "mode"
    const val ALLOW = "allow_tools"
    const val DENY = "deny_tools"
    const val DEFAULT_MODE = "auto"
}

/**
 * 从插件配置解析出的门禁规则。
 *
 * 工具名用英文逗号或换行分隔，禁止名单优先级最高；预置白名单（[PolicyRules.DEFAULT_ALLOW]）视同白名单成员。
 */
internal class PolicyRules(
    val mode: SecurityMode,
    val allow: Set<String>,
    val deny: Set<String>,
) {

    fun decide(tool: String): PolicyVerdict = when {
        mode == SecurityMode.OFF -> PolicyVerdict.Allow
        tool in deny -> PolicyVerdict.Deny("工具 [$tool] 在禁止名单中")
        tool in allow -> PolicyVerdict.Allow
        mode == SecurityMode.STRICT -> PolicyVerdict.Deny("工具 [$tool] 未在白名单中")
        tool in DEFAULT_ALLOW -> PolicyVerdict.Allow
        else -> PolicyVerdict.Confirm
    }

    companion object {
        /** 预置白名单：只读自有数据的工具，仅 auto 档免确认；
         *  严格档按字面白名单判定，不含默认项。用户显式禁止仍优先。 */
        val DEFAULT_ALLOW: Set<String> = setOf("memory_search", "history_search")

        fun from(config: PluginConfig): PolicyRules = PolicyRules(
            mode = SecurityMode.of(
                config.getString(SecurityConfigKeys.MODE, SecurityConfigKeys.DEFAULT_MODE),
            ),
            allow = parseNames(config.getString(SecurityConfigKeys.ALLOW)),
            deny = parseNames(config.getString(SecurityConfigKeys.DENY)),
        )

        private fun parseNames(raw: String): Set<String> = raw
            .split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
