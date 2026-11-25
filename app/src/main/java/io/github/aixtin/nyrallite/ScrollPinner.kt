package io.github.aixtin.nyrallite

import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView

/** 列表贴底：瞬时补齐与"本帧布局完成后、绘制前"贴底。 */
internal object ScrollPinner {

    /** 瞬时贴底：按当前剩余可滚距离补齐，对超高条目也钉在内容末尾。 */
    fun pin(rv: RecyclerView) {
        rv.scrollBy(
            0,
            rv.computeVerticalScrollRange() -
                rv.computeVerticalScrollExtent() -
                rv.computeVerticalScrollOffset(),
        )
    }

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
