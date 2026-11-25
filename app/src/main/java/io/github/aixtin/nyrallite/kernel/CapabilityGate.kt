package io.github.aixtin.nyrallite.kernel

/**
 * 权限声明校验的唯一实现。
 * 只做"声明了没有"的判断，不做"该不该给"的策略——策略在 security-policy 插件。
 */
internal object CapabilityGate {
    fun require(manifest: PluginManifest, permission: Permission) {
        if (permission !in manifest.permissions) {
            throw SecurityException("插件 ${manifest.id} 未在 manifest 声明权限 $permission")
        }
    }
}
