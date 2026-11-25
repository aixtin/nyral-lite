package io.github.aixtin.nyrallite.kernel

/** 单个插件的配置视图：key 自动落到该插件命名空间，插件之间互不可见。 */
interface PluginConfig {
    fun getString(key: String, def: String = ""): String
    fun put(key: String, value: String)
    fun remove(key: String)
}

/** [PluginConfig] 的命名空间实现：所有读写都带上前缀，避免插件互相踩 key。 */
internal class NamespacedConfig(
    private val backend: ConfigBackend,
    private val namespace: String,
) : PluginConfig {
    override fun getString(key: String, def: String): String =
        backend.read(namespace, key) ?: def

    override fun put(key: String, value: String) =
        backend.write(namespace, key, value)

    override fun remove(key: String) =
        backend.remove(namespace, key)
}
