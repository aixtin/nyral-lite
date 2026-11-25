package io.github.aixtin.nyrallite.kernel

import kotlin.reflect.KClass

/**
 * 记录单个插件通过 ctx.services.provide 注册的实例。
 * 插件卸载时由内核按逆序从服务容器移除，避免禁用后它注册的工具仍在清单里。
 */
internal class OwnedServices(
    private val view: ServiceRegistry,
    private val registry: DefaultServiceRegistry,
) : ServiceRegistry {

    private val provided = ArrayList<Pair<KClass<*>, Any>>()

    override fun <T : Any> provide(type: KClass<T>, instance: T, requires: Set<Permission>) {
        view.provide(type, instance, requires)
        provided.add(type to instance)
    }

    override fun <T : Any> get(type: KClass<T>): T = view.get(type)

    override fun <T : Any> getOrNull(type: KClass<T>): T? = view.getOrNull(type)

    override fun <T : Any> getAll(type: KClass<T>): List<T> = view.getAll(type)

    /** 卸载或安装失败时调用：逆序移除该插件注册过的服务。 */
    fun release() {
        for ((type, instance) in provided.asReversed()) registry.remove(type, instance)
        provided.clear()
    }
}
