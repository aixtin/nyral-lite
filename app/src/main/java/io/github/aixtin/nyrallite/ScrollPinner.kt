package io.github.aixtin.nyrallite

import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView

/** 列表贴底：瞬时补齐与"本帧布局完成后、绘制前"贴底。 */
internal object ScrollPinner {

    /**
     * 瞬时贴底：按当前剩余可滚距离补齐。
     *
     * computeVerticalScrollRange 对超高条目只是按已测得子项做推算，一次可能滚不到真正末尾，
     * 因此没到底就下一帧再补，最多 [MAX_PASSES] 轮，收敛后自动停止。
     */
    fun pin(rv: RecyclerView, pass: Int = 0) {
        rv.scrollBy(
            0,
            rv.computeVerticalScrollRange() -
                rv.computeVerticalScrollExtent() -
                rv.computeVerticalScrollOffset(),
        )
        if (pass < MAX_PASSES && rv.canScrollVertically(1)) {
            rv.post { pin(rv, pass + 1) }
        }
    }

    /** 收敛补齐的最大轮数，避免极端情况下无限重排。 */
    private const val MAX_PASSES = 4

    /**
     * 在"本帧布局完成后、绘制前"贴底: 内容增长与滚动在同帧合成。
     * 用 rv.post { scrollBy } 会让新内容先以未滚动位置渲染一帧再跳，
     * 视觉上就是逐帧抖动 (30帧感的来源之一)。
     */
    fun pinOnDraw(rv: RecyclerView) {
        rv.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    rv.viewTreeObserver.removeOnPreDrawListener(this)
                    pin(rv)
                    return true
                }
            },
        )
    }
}
