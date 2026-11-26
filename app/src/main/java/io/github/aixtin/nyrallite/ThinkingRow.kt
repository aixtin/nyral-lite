package io.github.aixtin.nyrallite

import android.os.Handler
import android.view.View

/**
 * 思考区：一条单行摘要栏的三态控制 + 限高抽屉 + 流式片段轮播。
 *
 * 流式中 : 最新思考片段（20 字，180ms 淡入淡出，句末即时切、其余 600ms 节流）+ 右侧字数
 * 首包前 : "正在处理..." 省略号动画，避免界面看起来像卡死
 * 结束后 : 首句摘要 …（N字） ›          点击展开/收起
 * 展开时 : 摘要行下方出现限高滚动抽屉
 *
 * 视图引用在 [AiMessageHolder]；展开时的滚动校正由适配器负责，这里只在点击时回调。
 */
internal class ThinkingRow(
    private val holder: AiMessageHolder,
    private val handler: Handler,
    private val onToggleClick: () -> Unit,
) {
    private var phase = PHASE_NONE
    private var animMsg: Message? = null
    private var lastSnippet = ""
    private var lastSnippetNs = 0L

    private val animator = ThinkingAnimator(
        handler = handler,
        onTick = { dots ->
            holder.label.setCurrentText(
                (if (phase == PHASE_PENDING) "正在处理" else "思考中") + ".".repeat(dots),
            )
        },
        isActive = { animMsg?.streaming == true },
    )

    /** 已写入抽屉的思考文本长度，避免重复 setText。 */
    private var textLastLength = -1

    init {
        holder.setupThinkingLabel()
    }

    /** holder 复用时重置全部状态，防止 phase 相同走 early-return 显示陈旧内容。 */
    fun reset() {
        animator.stop()
        animMsg = null
        holder.row.visibility = View.GONE
        holder.scroll.visibility = View.GONE
        holder.count.visibility = View.GONE
        holder.row.setOnClickListener(null)
        holder.row.isClickable = false
        holder.label.setCurrentText("")
        phase = PHASE_NONE
        textLastLength = -1
        lastSnippet = ""
    }

    fun updateThinking(msg: Message) {
        val target = targetPhase(msg)
        if (target == phase) {
            when (target) {
                PHASE_STREAMING -> refreshSnippet(msg)
                PHASE_DONE -> if (!msg.thinkingCollapsed &&
                    textLastLength != msg.thinking.length
                ) {
                    holder.text.text = msg.thinking
                    textLastLength = msg.thinking.length
                }
            }
            return
        }
        val animate = phase != PHASE_NONE && holder.itemView.isLaidOut
        when (target) {
            PHASE_NONE -> reset()
            PHASE_PENDING, PHASE_STREAMING -> {
                holder.row.visibility = View.VISIBLE
                holder.arrow.visibility = View.GONE
                holder.scroll.visibility = View.GONE
                holder.row.setOnClickListener(null)
                holder.row.isClickable = false
                animMsg = msg
                if (target == PHASE_PENDING) {
                    holder.count.visibility = View.GONE
                    animator.start()
                } else {
                    // 有正文了：停掉省略号动画，改为轮播最新片段
                    animator.stop()
                    holder.count.visibility = View.VISIBLE
                    lastSnippet = ""
                    refreshSnippet(msg, force = true)
                }
            }
            else -> {
                // 结束：原地把同一行文案换成摘要，位置/字号不变 -> 无切换感
                animator.stop()
                holder.row.visibility = View.VISIBLE
                holder.count.visibility = View.GONE
                holder.label.setText(ThinkingSummary.line(msg.thinking))
                holder.arrow.visibility = View.VISIBLE
                holder.row.isClickable = true
                holder.row.setOnClickListener { onToggleClick() }
                ThinkingDrawer.apply(msg, holder, animateArrow = animate)
            }
        }
        phase = target
    }

    /** 流式片段：内容变了才切，句末立即、其余按节流窗口，避免逐字闪。 */
    private fun refreshSnippet(msg: Message, force: Boolean = false) {
        val now = System.nanoTime()
        if (!force && !ThinkingSummary.shouldSwitch(msg.thinking, lastSnippet, lastSnippetNs, now)) {
            return
        }
        val text = ThinkingSummary.tail(msg.thinking)
        if (text.isEmpty()) return
        lastSnippet = text
        lastSnippetNs = now
        holder.label.setText(text)
        holder.count.text = "${msg.thinking.length}字"
    }

    fun hide() = reset()

    private fun targetPhase(msg: Message): Int = when {
        msg.thinking.isNotEmpty() && msg.streaming -> PHASE_STREAMING
        msg.thinking.isNotEmpty() -> PHASE_DONE
        // 已发送但首包还没到：显示"正在处理"，避免界面看起来像卡死
        msg.streaming -> PHASE_PENDING
        else -> PHASE_NONE
    }

    private companion object {
        const val PHASE_NONE = 0
        const val PHASE_STREAMING = 1
        const val PHASE_DONE = 2
        const val PHASE_PENDING = 3
    }
}
