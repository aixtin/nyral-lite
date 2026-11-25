package io.github.aixtin.nyrallite.kernel

import kotlin.reflect.KClass

/**
 * 类型化服务容器。
 *
 * provide 时声明该服务需要哪些权限；插件消费时，内核比对消费者 manifest，
 * 未声明对应权限直接拒绝。这是"插件只能拿声明过的能力"的落点。
 *
 * 同一类型可以注册**多个实例**：后注册者覆盖先注册者（getOrNull 返回最新），
 * 但 getAll 返回全部。用于 ToolSource、EventBus 订阅者等"多来源聚合"场景。
 */
interface ServiceRegistry {
    fun <T : Any> provide(type: KClass<T>, instance: T, requires: Set<Permission> = emptySet())
    fun <T : Any> get(type: KClass<T>): T
    fun <T : Any> getOrNull(type: KClass<T>): T?
    fun <T : Any> getAll(type: KClass<T>): List<T>
}

inline fun <reified T : Any> ServiceRegistry.get(): T = get(T::class)

inline fun <reified T : Any> ServiceRegistry.getOrNull(): T? = getOrNull(T::class)

inline fun <reified T : Any> ServiceRegistry.getAll(): List<T> = getAll(T::class)

inline fun <reified T : Any> ServiceRegistry.provide(
    instance: T,
    requires: Set<Permission> = emptySet(),
) = provide(T::class, instance, requires)
