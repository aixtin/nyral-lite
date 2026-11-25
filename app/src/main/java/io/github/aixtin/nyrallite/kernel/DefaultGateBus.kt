package io.github.aixtin.nyrallite.kernel

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * 拦截总线实现。优先级大的先执行；拦截器自身异常按拒绝处理（fail-closed），
 * 避免安全插件故障时静默放行。
 */
internal class DefaultGateBus : GateBus {

    private class Gate(
        val type: Class<*>,
        val priority: Int,
        val check: (Any) -> GateResult,
    )

    private val gates = CopyOnWriteArrayList<Gate>()

    @Suppress("UNCHECKED_CAST")
    override fun <E : Any> intercept(
        type: KClass<E>,
        priority: Int,
        gate: (E) -> GateResult,
    ): Disposable {
        val entry = Gate(type.java, priority) { gate(it as E) }
        gates.add(entry)
        return Disposable { gates.remove(entry) }
    }

    override fun <E : Any> check(event: E): GateResult {
        for (gate in gates.filter { it.type.isInstance(event) }.sortedByDescending { it.priority }) {
            val result = try {
                gate.check(event)
            } catch (e: Exception) {
                GateResult.Block("安全拦截器异常: ${e.message}")
            }
            if (result is GateResult.Block) return result
        }
        return GateResult.Allow
    }
}
