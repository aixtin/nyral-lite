package io.github.aixtin.nyrallite.kernel

/**
 * 插件契约。
 *
 * install 只做注册：provide 服务、on 事件、intercept 拦截、effect 副作用。
 * 不做耗时初始化（网络/磁盘预热放到首次使用时）——安装必须快且可控。
 *
 * install 抛异常 = 该插件安装失败：内核回滚它已注册的 effect，继续安装其他插件。
 */
interface Plugin {
    val manifest: PluginManifest
    fun install(ctx: PluginContext)
}
