package io.github.aixtin.nyrallite

import android.view.View
import android.view.ViewTreeObserver
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * 根布局消费状态栏 / 导航栏 / 键盘 inset：输入栏跟随键盘抬起。
 *
 * 键盘弹出会压缩列表高度。判定"弹出前是否贴底"必须发生在键盘出现的首帧、
 * 且列表仍是旧高度的时刻，此时 canScrollVertically(1) 反映的是弹出前的真实位置。
 * 之后每帧可视区缩小多少，就在绘制前把内容上移多少，与键盘动画同帧合成；
 * 用户在看历史（非贴底）时保持当前位置不动。
 *
 * 不能用滚动回调缓存贴底状态：列表被布局"挪"回底部（pin、重建、切会话恢复）
 * 时不会派发 dy≠0 的滚动回调，缓存会永久停在 false，导致后续键盘弹出全部失效。
 */
internal class KeyboardInsets(
    private val root: View,
    private val list: RecyclerView,
) {
    /** 键盘弹出前列表是否贴底；只由键盘出现首帧（旧布局）决定。 */
    private var pinnedBeforeIme = false
    /** 上一帧键盘是否可见，用于捕捉"从无到有"与"从有到无"的边沿。 */
    private var imeVisible = false
    /** 本帧待补的滚动距离：一帧内可能多次布局，累积后统一补一次。 */
    private var pendingDelta = 0
    private var scrollScheduled = false

    fun install() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val nowIme = insets.isVisible(WindowInsetsCompat.Type.ime())

            if (nowIme && !imeVisible) {
                // 键盘刚弹出：此刻 padding 还未应用，列表仍是旧高度，
                // canScrollVertically(1) 得到的是弹出前的真实位置，才可用作判定
                pinnedBeforeIme = !list.canScrollVertically(1)
            } else if (!nowIme && imeVisible) {
                // 键盘收起：清掉判定，避免收键盘那次高度变化被误判为需要跟随
                pinnedBeforeIme = false
            }
            imeVisible = nowIme

            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, maxOf(bars.bottom, ime.bottom))
            insets
        }
        list.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val oldHeight = oldBottom - oldTop
            val newHeight = bottom - top
            if (!pinnedBeforeIme || oldHeight <= 0 || newHeight >= oldHeight) {
                return@addOnLayoutChangeListener
            }
            // 可视区缩小多少，内容就上移多少：按高度差精确跟随，不用估算的可滚距离，
            // 避免一次跳到末尾而键盘还在动画造成的错位
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
