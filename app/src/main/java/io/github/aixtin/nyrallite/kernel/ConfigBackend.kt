package io.github.aixtin.nyrallite.kernel

/**
 * 配置持久化后端。内核只负责按 pluginId 分命名空间，不关心存储介质；
 * 标准实现由 storage-prefs 插件提供，插件缺失时用内存实现兜底。
 */
interface ConfigBackend {
    fun read(ns: String, key: String): String?
    fun write(ns: String, key: String, value: String)
    fun remove(ns: String, key: String)
}

/** 进程内内存后端：重启即失。仅用于内核自测或存储插件缺失时兜底。 */
class InMemoryConfigBackend : ConfigBackend {
    private val data = HashMap<String, HashMap<String, String>>()

    override fun read(ns: String, key: String): String? = data[ns]?.get(key)

    override fun write(ns: String, key: String, value: String) {
        data.getOrPut(ns) { HashMap() }[key] = value
    }

    override fun remove(ns: String, key: String) {
        data[ns]?.remove(key)
    }
}
