package io.github.aixtin.nyrallite

import android.os.Handler
import android.os.Looper
import androidx.recyclerview.widget.RecyclerView

/**
 * 流式回复的打字机：网络整块到达的文本先入缓冲，显示指针逐帧前进，
 * 积压越多步长越大自动追赶，避免一串一串蹦。
 *
 * 只负责“一条 AI 消息的正文/思考如何逐步上屏 + 贴底”，
 * 何时发起下一轮、何时收尾由 [ChatController] 决定。
 */
class Typewriter(
    private val rv: RecyclerView,
    private val adapter: ChatAdapter,
    private val messages: MutableList<Message>,
    private val onDrained: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val text = StringBuilder()
    private val thinking = StringBuilder()

    private var pos = -1
    private var displayedLen = 0
    private var scheduled = false
    private var finished = false

    /** 当前流式消息末尾段是否是表格（updateAi 返回）：表格 measure 重，自动降 30fps。 */
    private var tailIsTable = false

    /** 用户手指/惯性滚动进行中：冻结显示指针，避免每帧 requestLayout 与手势抢帧。 */
    var paused = false

    /** 当前模型轮次已接收的正文全文（含尚未上屏的部分）。 */
    val textSnapshot: String get() = text.toString()

    val thinkingSnapshot: String get() = thinking.toString()

    private val tick = object : Runnable {
        override fun run() {
            if (pos !in messages.indices) {
                scheduled = false
                return
            }
            val target = text.length
            val heavy = target > 6000
            val backlog = target - displayedLen
            if (backlog <= 0 && !finished) {
                // 已追平缓冲区, 停摆等下一块网络数据 (append 会重新调度)
                scheduled = false
                return
            }
            if (paused) {
                // 手势滚动中冻结指针, 松手后恢复
                if (finished && backlog <= 0) {
                    scheduled = false
                    onDrained()
                    return
                }
                reschedule()
                return
            }
            if (backlog > 0) {
                val step = when {
                    backlog > 160 -> if (heavy) 16 else 10
                    backlog > 48 -> if (heavy) 8 else 4
                    else -> 1
                }
                displayedLen = minOf(target, displayedLen + step)
                // 贴底判定要在内容变化前做; 滚动不用 scrollToPosition
                val wasPinned = !rv.canScrollVertically(1)
                tailIsTable = adapter.updateAi(
                    pos,
                    text.substring(0, displayedLen),
                    thinking.toString(),
                )
                if (wasPinned) pinToBottomOnDraw()
            }
            if (finished && displayedLen >= target) {
                scheduled = false
                onDrained()
            } else {
                reschedule()
            }
        }
    }

    /** 开始渲染一条新的 AI 消息。 */
    fun begin(messagePos: Int) {
        handler.removeCallbacks(tick)
        pos = messagePos
        text.setLength(0)
        thinking.setLength(0)
        displayedLen = 0
        scheduled = false
        finished = false
        tailIsTable = false
    }

    fun append(content: String, reasoning: String) {
        if (content.isNotEmpty()) text.append(content)
        if (reasoning.isNotEmpty()) thinking.append(reasoning)
        ensure()
    }

    /** 声明本轮结束：显示指针追上缓冲后回调 onDrained。 */
    fun finish() {
        finished = true
        ensure()
    }

    /** 还有工具轮要继续：重新打开"未收尾"开关。 */
    fun resume() {
        finished = false
    }

    /** 停止动画；缓冲保留，供收尾时读取全文。 */
    fun stop() {
        handler.removeCallbacks(tick)
        scheduled = false
        pos = -1
    }

    /** 瞬时贴底；实现见 [ScrollPinner]。 */
    fun pinToBottom() = ScrollPinner.pin(rv)

    /** 本帧布局完成后贴底；实现见 [ScrollPinner]。 */
    fun pinToBottomOnDraw() = ScrollPinner.pinOnDraw(rv)

    private fun ensure() {
        if (!scheduled) {
            scheduled = true
            reschedule()
        }
    }

    /** 表格或长文 measure 重, 降到稳定 30fps; 纯文本跟满 vsync 60fps。 */
    private fun reschedule() {
        if (text.length > 6000 || tailIsTable) handler.postDelayed(tick, 33L)
        else rv.postOnAnimation(tick)
    }
}
