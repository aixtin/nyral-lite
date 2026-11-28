package io.github.aixtin.nyrallite.kernel

/**
 * 插件可申请的系统能力。
 *
 * 只是声明维度：插件必须在 [PluginManifest.permissions] 里写明，运行时访问前
 * 通过 [PluginContext.require] 校验。内核不含"哪个插件该给什么"的策略，
 * 那是 security-policy 插件的事。
 */
enum class Permission {
    NETWORK,
    FILES,
    CLIPBOARD,
}
