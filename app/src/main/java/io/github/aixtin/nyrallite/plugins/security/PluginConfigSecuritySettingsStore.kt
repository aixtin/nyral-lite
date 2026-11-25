package io.github.aixtin.nyrallite.plugins.security

import io.github.aixtin.nyrallite.contract.SecuritySettings
import io.github.aixtin.nyrallite.contract.SecuritySettingsStore
import io.github.aixtin.nyrallite.contract.SecurityMode
import io.github.aixtin.nyrallite.kernel.PluginConfig

/**
 * [SecuritySettingsStore] 实现：读写 security-policy 插件命名空间下的配置。
 * 门禁判定每次都会重新读取同一份配置，所以保存后立即生效，无需重启。
 */
internal class PluginConfigSecuritySettingsStore(
    private val config: PluginConfig,
) : SecuritySettingsStore {

    override fun load(): SecuritySettings = SecuritySettings(
        mode = SecurityMode.of(
            config.getString(SecurityConfigKeys.MODE, SecurityConfigKeys.DEFAULT_MODE),
        ),
        allowTools = config.getString(SecurityConfigKeys.ALLOW),
        denyTools = config.getString(SecurityConfigKeys.DENY),
    )

    override fun save(settings: SecuritySettings) {
        config.put(SecurityConfigKeys.MODE, settings.mode.raw)
        config.put(SecurityConfigKeys.ALLOW, settings.allowTools.trim())
        config.put(SecurityConfigKeys.DENY, settings.denyTools.trim())
    }
}
