package io.github.aixtin.nyrallite

import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView

/** 思考抽屉的展开/收起与滚动校正。 */
internal object ThinkingDrawer {

    /**
     * 手动展开/收起：切换后下一帧校正一次滚动，锁定摘要行的屏幕位置，抽屉只向下伸展。
     * 不在 layout 回调里校正——那会和 RecyclerView 自身布局互相触发导致抖动。
     */
    fun toggle(msg: Message, h: AiMessageHolder, recycler: RecyclerView?) {
        msg.thinkingCollapsed = !msg.thinkingCollapsed
        if (recycler == null) {
            h.thinking.applyDrawer(msg, animateArrow = true)
            return
        }
        val before = IntArray(2)
        h.row.getLocationOnScreen(before)
        h.thinking.applyDrawer(msg, animateArrow = true)
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
