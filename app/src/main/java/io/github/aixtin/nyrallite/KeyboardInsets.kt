package io.github.aixtin.nyrallite

import android.view.View
import android.view.ViewTreeObserver
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * 键盘弹出时贴底内容跟随上移。
 *
 * 两种系统行为都要兼顾：
 *  - Android 15+ 忽略 adjustResize，键盘通过 IME inset 压缩列表；
 *  - Android 12~14 按 adjustResize 直接压缩窗口，收不到可用的 IME inset 事件。
 * 因此不依赖 IME 事件，而是每帧记录"是否贴底"，在列表高度变小那次布局里
 * 用上一帧（即变化前）的状态决定是否把内容上移同样距离。
 */
internal class KeyboardInsets(
    private val root: View,
    private val list: RecyclerView,
) {
    /** 上一帧列表是否贴底；布局变化发生在 preDraw 之前，所以这里读到的是变化前的状态。 */
    private var pinned = false
    private var pendingDelta = 0
    private var scrollScheduled = false

    fun install() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, maxOf(bars.bottom, ime.bottom))
            insets
        }
        list.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    pinned = !list.canScrollVertically(1)
                    return true
                }
            },
        )
        list.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val oldHeight = oldBottom - oldTop
            val newHeight = bottom - top
            if (oldHeight <= 0 || newHeight >= oldHeight || !pinned) {
                return@addOnLayoutChangeListener
            }
            // 可视区缩小多少，内容就上移多少：用高度差精确跟随，避免估算误差造成的错位
            pendingDelta += oldHeight - newHeight
            scheduleScroll()
        }
    }

    /** 在绘制前一次性补上本帧累积的位移，与键盘动画同帧合成。 */
    private fun scheduleScroll() {
        if (scrollScheduled) return
        scrollScheduled = true
        list.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    list.viewTreeObserver.removeOnPreDrawListener(this)
                    scrollScheduled = false
                    val delta = pendingDelta
                    pendingDelta = 0
                    if (delta > 0) list.scrollBy(0, delta)
                    return true
                }
            },
        )
    }
}
