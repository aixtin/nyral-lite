package io.github.aixtin.nyrallite.kernel

import kotlin.reflect.KClass

/**
 * 交给插件的受限服务视图。
 * provide 正常放行（插件注册自己的服务）；get/getAll 用该插件 manifest 做权限校验。
 */
internal class PluginServiceView(
    private val delegate: DefaultServiceRegistry,
    private val manifest: PluginManifest,
) : ServiceRegistry {

    override fun <T : Any> provide(type: KClass<T>, instance: T, requires: Set<Permission>) =
        delegate.provide(type, instance, requires)

    override fun <T : Any> get(type: KClass<T>): T = delegate.getFor(manifest, type)

    override fun <T : Any> getOrNull(type: KClass<T>): T? = delegate.getOrNullFor(manifest, type)

    override fun <T : Any> getAll(type: KClass<T>): List<T> = delegate.getAllFor(manifest, type)
}
