package io.github.aixtin.nyrallite.kernel

import android.content.Context

/**
 * 插件运行门面：插件能接触到的全部内核能力都在这里，没有第二个入口。
 *
 * 关键约束：
 * - [services] 是受限视图，消费服务前按 manifest 校验权限；
 * - [require] 显式声明系统能力，未声明即抛；
 * - [effect] 注册的一切副作用（事件订阅/拦截器/资源）在卸载时自动撤销。
 */
interface PluginContext {
    val pluginId: String

    /** 只用于构建 View / 拿系统服务；业务依赖优先走 [services]。 */
    val appContext: Context

    val services: ServiceRegistry
    val events: EventBus
    val gates: GateBus
    val config: PluginConfig

    fun require(permission: Permission)

    fun effect(onDispose: () -> Unit): Disposable
}
