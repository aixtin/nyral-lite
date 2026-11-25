package io.github.aixtin.nyrallite

import android.os.Handler
import android.view.View

/**
 * 思考区：一条单行摘要栏的三态控制 + 限高抽屉 + 流式省略号动画。
 *
 * 流式中 : "思考中..." / "正在处理..."   灰色, 无箭头, 不可点
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
    private val animator = ThinkingAnimator(
        handler = handler,
        onTick = { dots ->
            holder.label.text =
                (if (phase == PHASE_PENDING) "正在处理" else "思考中") + ".".repeat(dots)
        },
        isActive = { animMsg?.streaming == true },
    )

    /** 已写入抽屉的思考文本长度，避免重复 setText。 */
    private var textLastLength = -1

    /** holder 复用时重置全部状态，防止 phase 相同走 early-return 显示陈旧内容。 */
    fun reset() {
        animator.stop()
        animMsg = null
        holder.row.visibility = View.GONE
        holder.scroll.visibility = View.GONE
        holder.row.setOnClickListener(null)
        holder.row.isClickable = false
        phase = PHASE_NONE
        textLastLength = -1
    }

    fun updateThinking(msg: Message) {
        val target = targetPhase(msg)
        if (target == phase) {
            // 同状态内只有展开抽屉的内容可能变化
            if (target == PHASE_DONE && !msg.thinkingCollapsed &&
                textLastLength != msg.thinking.length
            ) {
                holder.text.text = msg.thinking
                textLastLength = msg.thinking.length
            }
            return
        }
        val animate = phase != PHASE_NONE && holder.itemView.isLaidOut
        when (target) {
            PHASE_NONE -> hide()
            PHASE_STREAMING, PHASE_PENDING -> {
                holder.row.visibility = View.VISIBLE
                holder.label.setTextColor(0xFFA0A0A0.toInt())
                holder.label.text = if (target == PHASE_PENDING) "正在处理" else "思考中"
                holder.arrow.visibility = View.GONE
                holder.scroll.visibility = View.GONE
                holder.row.setOnClickListener(null)
                holder.row.isClickable = false
                animMsg = msg
                animator.start()
            }
            else -> {
                // 结束：原地把同一行文案换成摘要，位置/字号不变 -> 无切换感
                animator.stop()
                holder.row.visibility = View.VISIBLE
                holder.label.text = ThinkingSummary.line(msg.thinking)
                holder.label.setTextColor(0xFF9A9A9A.toInt())
                holder.arrow.visibility = View.VISIBLE
                holder.row.isClickable = true
                holder.row.setOnClickListener { onToggleClick() }
                applyDrawer(msg, animateArrow = animate)
            }
        }
        phase = target
    }

    /** 折叠：箭头向右 ▶ 抽屉收起；展开：箭头向下 ▼ 抽屉出现（限高内部滚动）。 */
    fun applyDrawer(msg: Message, animateArrow: Boolean) {
        if (msg.thinkingCollapsed) {
            holder.scroll.visibility = View.GONE
            holder.scroll.alpha = 1f
            if (animateArrow) holder.arrow.animate().rotation(90f).setDuration(200).start()
            else holder.arrow.rotation = 90f
        } else {
            holder.scroll.visibility = View.VISIBLE
            if (textLastLength != msg.thinking.length) {
                holder.text.text = msg.thinking
                textLastLength = msg.thinking.length
            }
            if (animateArrow) {
                holder.scroll.alpha = 0f
                holder.scroll.animate().alpha(1f).setDuration(140).start()
                holder.arrow.animate().rotation(-90f).setDuration(200).start()
            } else {
                holder.scroll.alpha = 1f
                holder.arrow.rotation = -90f
            }
        }
    }

    fun hide() {
        animator.stop()
        animMsg = null
        holder.row.visibility = View.GONE
        holder.scroll.visibility = View.GONE
        holder.row.setOnClickListener(null)
        holder.row.isClickable = false
        phase = PHASE_NONE
    }

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
