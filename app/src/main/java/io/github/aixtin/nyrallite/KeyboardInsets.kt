package io.github.aixtin.nyrallite

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * 根布局消费状态栏 / 导航栏 / 键盘 inset：输入栏跟随键盘抬起。
 *
 * 键盘（无论是窗口压缩还是 inset padding）都会压缩列表高度。持续记录列表是否贴底，
 * 高度变化时若变化前正好贴底，就在绘制前重新贴底，让最后几条消息跟着抬起；
 * 用户正在看历史（不在底部）时保持当前位置不动。
 */
internal class KeyboardInsets(
    private val root: View,
    private val list: RecyclerView,
) {
    /** 最近一次滚动后的贴底状态，用来判断"高度变化前"是否在底部。 */
    private var atBottom = true

    fun install() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, maxOf(bars.bottom, ime.bottom))
            insets
        }
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // 高度变化时也会派发 dy=0 的滚动回调，此时不能刷新贴底状态，
                // 否则会先被"缩小后的视图"判成不贴底，错过本次抬起
                if (dy == 0) return
                atBottom = !recyclerView.canScrollVertically(1)
            }
        })
        list.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val oldHeight = oldBottom - oldTop
            if (oldHeight > 0 && bottom - top != oldHeight && atBottom) {
                ScrollPinner.pinOnDraw(list)
            }
        }
    }
}
