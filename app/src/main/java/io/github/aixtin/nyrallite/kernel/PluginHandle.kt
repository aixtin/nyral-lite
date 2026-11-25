package io.github.aixtin.nyrallite.kernel

import android.content.Context

/**
 * 单个插件的安装句柄：持有它的 effect 回收盒与安装状态。
 * 安装失败时立即回滚该插件已注册的副作用，不污染内核。
 */
internal class PluginHandle(val plugin: Plugin) {

    private val effects = DisposableGroup()

    val manifest: PluginManifest get() = plugin.manifest

    var installed: Boolean = false
        private set

    fun install(
        appContext: Context,
        services: ServiceRegistry,
        events: EventBus,
        gates: GateBus,
        config: PluginConfig,
    ) {
        val ctx = DefaultPluginContext(
            pluginId = manifest.id,
            appContext = appContext,
            services = services,
            events = events,
            gates = gates,
            config = config,
            manifest = manifest,
            effects = effects,
        )
        try {
            plugin.install(ctx)
            installed = true
        } catch (e: Exception) {
            effects.dispose()
            throw e
        }
    }

    fun dispose() {
        effects.dispose()
        installed = false
    }
}
