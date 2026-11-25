package io.github.aixtin.nyrallite.kernel

/** 按 pluginId 发配置视图；同一个插件每次拿到的是同一个视图。 */
class ConfigStore(private val backend: ConfigBackend) {
    private val views = HashMap<String, PluginConfig>()

    fun forPlugin(pluginId: String): PluginConfig =
        views.getOrPut(pluginId) { NamespacedConfig(backend, pluginId) }
}
