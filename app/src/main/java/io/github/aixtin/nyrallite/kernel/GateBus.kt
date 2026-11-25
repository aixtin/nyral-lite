package io.github.aixtin.nyrallite.kernel

import kotlin.reflect.KClass

/** 拦截结果：允许继续，或拒绝并给出可展示的原因。 */
sealed interface GateResult {
    data object Allow : GateResult
    data class Block(val reason: String) : GateResult
}

/**
 * 可拦截事件总线：按优先级串行询问，第一个 Block 立即生效。
 *
 * 安全钩子 tool.before-call 走这里：security-policy 插件注册拦截器，
 * 工具执行方在真正调用前 await check()。内核只提供机制，不含任何策略。
 */
interface GateBus {
    fun <E : Any> intercept(
        type: KClass<E>,
        priority: Int = 0,
        gate: (E) -> GateResult,
    ): Disposable

    fun <E : Any> check(event: E): GateResult
}
