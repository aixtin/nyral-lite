package io.github.aixtin.nyrallite.kernel

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * 事件总线实现：同步派发，按订阅类型过滤。
 * 单个监听器抛异常只影响它自己，不打断其余订阅者和 emit 调用方。
 */
internal class DefaultEventBus : EventBus {

    private class Subscription(val type: Class<*>, val listener: (Any) -> Unit)

    private val subscriptions = CopyOnWriteArrayList<Subscription>()

    override fun emit(event: Any) {
        for (sub in subscriptions) {
            if (!sub.type.isInstance(event)) continue
            try {
                sub.listener(event)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <E : Any> on(type: KClass<E>, listener: (E) -> Unit): Disposable {
        val sub = Subscription(type.java) { listener(it as E) }
        subscriptions.add(sub)
        return Disposable { subscriptions.remove(sub) }
    }
}
