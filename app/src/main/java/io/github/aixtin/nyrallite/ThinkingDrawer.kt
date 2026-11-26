package io.github.aixtin.nyrallite

import android.view.View
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView

/** 思考抽屉的展开/收起与滚动校正。 */
internal object ThinkingDrawer {

    /**
     * 按消息的折叠状态刷新抽屉：收起时隐藏并让箭头指向右，展开时显示并让箭头指向下。
     * 首次绑定（[animateArrow] 为 false）直接落到终态，不做动画。
     */
    fun apply(msg: Message, h: AiMessageHolder, animateArrow: Boolean) {
        if (msg.thinkingCollapsed) {
            h.scroll.visibility = View.GONE
            h.scroll.alpha = 1f
            if (animateArrow) h.arrow.animate().rotation(90f).setDuration(200).start()
            else h.arrow.rotation = 90f
        } else {
            h.scroll.visibility = View.VISIBLE
            if (h.text.text.toString() != msg.thinking) h.text.text = msg.thinking
            if (animateArrow) {
                h.scroll.alpha = 0f
                h.scroll.animate().alpha(1f).setDuration(140).start()
                h.arrow.animate().rotation(-90f).setDuration(200).start()
            } else {
                h.scroll.alpha = 1f
                h.arrow.rotation = -90f
            }
        }
    }

    /**
     * 手动展开/收起：切换后下一帧校正一次滚动，锁定摘要行的屏幕位置，抽屉只向下伸展。
     * 不在 layout 回调里校正——那会和 RecyclerView 自身布局互相触发导致抖动。
     */
    fun toggle(msg: Message, h: AiMessageHolder, recycler: RecyclerView?) {
        msg.thinkingCollapsed = !msg.thinkingCollapsed
        if (recycler == null) {
            apply(msg, h, animateArrow = true)
            return
        }
        val before = IntArray(2)
        h.row.getLocationOnScreen(before)
        apply(msg, h, animateArrow = true)
        // 等这一帧布局完成 (onPreDraw) 再读真实坐标并校正一次
        recycler.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    recycler.viewTreeObserver.removeOnPreDrawListener(this)
                    val after = IntArray(2)
                    h.row.getLocationOnScreen(after)
                    val dy = after[1] - before[1]
                    if (dy != 0) recycler.scrollBy(0, dy)
                    return true
                }
            },
        )
    }
}
