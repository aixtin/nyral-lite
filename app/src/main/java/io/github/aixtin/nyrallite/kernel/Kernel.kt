package io.github.aixtin.nyrallite.kernel

import android.content.Context

/**
 * 插件内核：只做五件事——注册/排序、服务容器、事件总线、可拦截总线、配置分层。
 * 这里不出现任何聊天、Markdown、存储等业务概念，也不 import 主体或插件。
 *
 * 加载模式（混合热加载 + 冷加载）：
 *   - 热加载：start() 之后仍可 install/uninstall 单个插件，ServiceRegistry 实时查 Map
 *     天然支持热生效；ToolRegistry / EventBus / GateBus 也都是运行时遍历。
 *   - 冷加载：插件声明 Android 组件（BroadcastReceiver / ForegroundService 等）时，
 *     内核检查 manifest.needsColdStart() 返回 true，调用方应提示用户重启 App。
 *
 * 典型生命周期：
 *   冷启动阶段：install(内置列表) → start()
 *   运行时阶段：installOne(新插件) / uninstall(旧插件) ← 热加载
 */
class Kernel(
    context: Context,
    configBackend: ConfigBackend = InMemoryConfigBackend(),
) {
    private val appContext: Context = context.applicationContext
    private val registry = DefaultServiceRegistry()
    private val configStore = ConfigStore(configBackend)
    private val handles = LinkedHashMap<String, PluginHandle>()
    private var started = false

    /** 主体侧的特权视图：不做权限校验。 */
    val services: ServiceRegistry get() = registry

    val events: EventBus = DefaultEventBus()

    val gates: GateBus = DefaultGateBus()

    /**
     * 批量安装插件（按依赖拓扑排序）。单个插件失败只回滚它自己，不影响其他插件。
     * start() 之前调用 = 冷启动批次；start() 之后调用 = 热加载批次。
     */
    fun install(plugins: List<Plugin>) {
        for (plugin in PluginRegistry.order(plugins)) {
            installOne(plugin)
        }
    }

    /**
     * 安装单个插件——热加载入口。
     * 已安装同 id 则跳过（不抛异常）。
     * 插件抛异常时自动回滚 effect，内核状态不受污染。
     *
     * 调用方应在安装前检查 [PluginManifest.needsColdStart]：
     *   - false：热加载后立即生效
     *   - true：插件声明了 Android 组件，需冷启动才能注册到系统
     */
    fun installOne(plugin: Plugin): Boolean {
        val id = plugin.manifest.id
        if (handles.containsKey(id)) return false
        val handle = PluginHandle(plugin)
        try {
            handle.install(
                appContext = appContext,
                services = PluginServiceView(registry, plugin.manifest),
                events = events,
                gates = gates,
                config = configStore.forPlugin(id),
            )
            handles[id] = handle
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * 卸载单个插件——热卸载入口。
     * 插件注册的 Service / Tool / Event / Gate 回调通过 PluginHandle.dispose() 清理。
     */
    fun uninstall(pluginId: String): Boolean {
        val handle = handles.remove(pluginId) ?: return false
        return runCatching { handle.dispose() }.isSuccess
    }

    /** 启动内核（可重复调用，幂等）。 */
    fun start() {
        started = true
    }

    /** 逆序卸载全部插件，撤销它们注册的所有副作用。 */
    fun stop() {
        if (!started) return
        for (handle in handles.values.toList().asReversed()) {
            try {
                handle.dispose()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        handles.clear()
        started = false
    }

    fun isInstalled(pluginId: String): Boolean = handles[pluginId]?.installed == true

    /** 列出所有已安装插件的 manifest（快照，不随后续 uninstall 自动更新）。 */
    fun installedManifests(): List<PluginManifest> =
        handles.values.map { it.manifest }
}
