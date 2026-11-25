package io.github.aixtin.nyrallite.kernel

import kotlin.reflect.KClass

/** 服务定义：实例 + 消费该服务所需的权限。 */
private class ServiceDefinition(val instance: Any, val requires: Set<Permission>)

/**
 * 服务容器实现。
 *
 * - 主体通过 [get]/[getOrNull]/[getAll] 直接访问（特权消费者，不做权限校验）；
 * - 插件通过 [PluginServiceView] 访问，消费前按 manifest 校验权限。
 *
 * 同一类型可以注册多个实例，后注册者覆盖先注册者（getOrNull 返回最新），
 * [getAll] 返回按注册顺序的全部实例。用于 ToolSource 等多来源聚合场景。
 */
internal class DefaultServiceRegistry : ServiceRegistry {

    private val definitions = LinkedHashMap<KClass<*>, MutableList<ServiceDefinition>>()

    override fun <T : Any> provide(type: KClass<T>, instance: T, requires: Set<Permission>) {
        val list = definitions.getOrPut(type) { ArrayList() }
        // 同类型同实例不重复注册（idempotent）
        list.removeAll { it.instance === instance }
        list.add(ServiceDefinition(instance, requires))
    }

    override fun <T : Any> get(type: KClass<T>): T =
        getOrNull(type) ?: throw IllegalStateException("服务未注册: ${type.qualifiedName}")

    override fun <T : Any> getOrNull(type: KClass<T>): T? {
        val list = definitions[type] ?: return null
        if (list.isEmpty()) return null
        @Suppress("UNCHECKED_CAST")
        return list.last().instance as T
    }

    override fun <T : Any> getAll(type: KClass<T>): List<T> {
        val list = definitions[type] ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return list.map { it.instance as T }
    }

    /** 插件侧访问：未声明服务所需权限则抛 SecurityException。 */
    fun <T : Any> getFor(consumer: PluginManifest, type: KClass<T>): T =
        getOrNullFor(consumer, type)
            ?: throw IllegalStateException("服务未注册: ${type.qualifiedName}")

    fun <T : Any> getOrNullFor(consumer: PluginManifest, type: KClass<T>): T? {
        val list = definitions[type] ?: return null
        if (list.isEmpty()) return null
        val def = list.last()
        val missing = def.requires - consumer.permissions
        if (missing.isNotEmpty()) {
            throw SecurityException(
                "插件 ${consumer.id} 未声明权限 $missing，拒绝访问服务 ${type.simpleName}",
            )
        }
        @Suppress("UNCHECKED_CAST")
        return def.instance as T
    }

    fun <T : Any> getAllFor(consumer: PluginManifest, type: KClass<T>): List<T> {
        val list = definitions[type] ?: return emptyList()
        return list.mapNotNull { def ->
            val missing = def.requires - consumer.permissions
            if (missing.isNotEmpty()) null
            else @Suppress("UNCHECKED_CAST") (def.instance as T)
        }
    }
}
