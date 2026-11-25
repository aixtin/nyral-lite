package io.github.aixtin.nyrallite.kernel

import android.content.Context

/** [PluginContext] 的唯一实现：把内核能力裁剪成单个插件可见的范围。 */
internal class DefaultPluginContext(
    override val pluginId: String,
    override val appContext: Context,
    override val services: ServiceRegistry,
    override val events: EventBus,
    override val gates: GateBus,
    override val config: PluginConfig,
    private val manifest: PluginManifest,
    private val effects: DisposableGroup,
) : PluginContext {

    override fun require(permission: Permission) =
        CapabilityGate.require(manifest, permission)

    override fun effect(onDispose: () -> Unit): Disposable =
        Disposable(onDispose).also { effects.add(it) }
}
