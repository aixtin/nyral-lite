package io.github.aixtin.nyrallite.kernel

import kotlin.reflect.KClass

/**
 * 类型化事件总线：广播事实（某个动作已发生），不返回值、不可拦截。
 * 需要拦截的语义走 [GateBus]，不要往这里塞策略。
 */
interface EventBus {
    fun emit(event: Any)

    /** 订阅指定类型；返回的 [Disposable] 用于退订（通常交给 ctx.effect）。 */
    fun <E : Any> on(type: KClass<E>, listener: (E) -> Unit): Disposable
}
