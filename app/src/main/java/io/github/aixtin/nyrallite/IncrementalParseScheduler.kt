package io.github.aixtin.nyrallite

import io.github.aixtin.nyrallite.contract.ContentRenderer
import io.github.aixtin.nyrallite.contract.RenderSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 流式分段的后台解析调度。
 *
 * 策略 = 只保留最新快照：不取消正在飞行的 parse；它完成回主线程时，若期间来了更新的
 * 内容则丢弃并重 parse 最新快照。这样长文流式（每帧都可能新内容）不会堆积任务雪崩。
 */
internal class IncrementalParseScheduler(private val renderer: ContentRenderer) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var version = 0
    private var inFlight = false
    private var latestSnapshot: (() -> String)? = null
    private var latestOnParsed: ((String, List<RenderSegment>) -> Boolean)? = null

    /**
     * 请求解析最新快照。[onParsed] 返回 false 表示因 holder 回收/消息失效而放弃本次结果。
     * 解析在飞时只更新"最新快照 + 最新回调"，完成后用最新的一份重跑，回调不会被旧请求覆盖。
     */
    fun request(
        snapshot: () -> String,
        onParsed: (String, List<RenderSegment>) -> Boolean,
    ) {
        latestSnapshot = snapshot
        latestOnParsed = onParsed
        version++
        if (inFlight) return
        inFlight = true
        launch()
    }

    fun dispose() {
        scope.coroutineContext[Job]?.cancel()
    }

    private fun launch() {
        val startVersion = version
        val snapshot = latestSnapshot ?: run {
            inFlight = false
            return
        }
        val text = snapshot()
        scope.launch {
            val segments = withContext(Dispatchers.Default) { renderer.segments(text) }
            if (startVersion != version) {
                // 期间来了更新的内容：丢弃本次，追最新快照（保持 inFlight = true）
                launch()
                return@launch
            }
            try {
                latestOnParsed?.invoke(text, segments)
            } finally {
                inFlight = false
            }
        }
    }
}
