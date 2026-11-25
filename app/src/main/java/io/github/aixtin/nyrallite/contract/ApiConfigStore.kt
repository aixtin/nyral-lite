package io.github.aixtin.nyrallite.contract

/**
 * API 配置存取接口。
 * 实现由 storage-prefs 插件提供，主体（配置页/启动检查）只认这个接口。
 */
interface ApiConfigStore {
    fun load(): ApiConfig
    fun save(config: ApiConfig)
    fun isConfigured(): Boolean
}
