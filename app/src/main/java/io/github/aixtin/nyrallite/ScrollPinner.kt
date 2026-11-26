package io.github.aixtin.nyrallite

import android.util.Log
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView

/** 列表贴底：瞬时补齐与"本帧布局完成后、绘制前"贴底。 */
internal object ScrollPinner {

    /**
     * 距底部不超过 [toleranceDp] 即视为贴底。
     * 留容差是因为 canScrollVertically 差 1px 就判 false，内边距/内容微抖都会让跟随失效。
     */
    fun nearBottom(rv: RecyclerView, toleranceDp: Int = 16): Boolean {
        val tolerance = (toleranceDp * rv.resources.displayMetrics.density).toInt()
        val remaining = rv.computeVerticalScrollRange() -
            rv.computeVerticalScrollExtent() - rv.computeVerticalScrollOffset()
        return remaining <= tolerance
    }

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
     * 收敛贴底：computeVerticalScrollRange 对超高条目只是按已测得子项推算，一次可能滚不到
     * 真正末尾，因此没到底就下一帧再补，最多 [MAX_PASSES] 轮，收敛后自动停止。
     *
     * 只用于会话加载这类静态场景。键盘动画与流式渲染必须走单次 [pin]，
     * 否则多轮补会表现为内容分几步跳动，大块内容（图片）看着就是残影。
     */
    fun pinStable(rv: RecyclerView, pass: Int = 0) {
        pin(rv)
        if (pass < MAX_PASSES && rv.canScrollVertically(1)) {
            rv.post { pinStable(rv, pass + 1) }
        }
    }

    /**
     * 贴底跟随，但最多把本条消息推到"顶部贴住列表顶部"为止：之后停住，
     * 用户可以从头往下读，新内容继续在下方生成。
     *
     * [onCapped] 在本帧真的被截停时回调，调用方据此关闭后续跟随。
     */
    fun pinUntilTop(rv: RecyclerView, position: Int, onCapped: () -> Unit) {
        rv.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    rv.viewTreeObserver.removeOnPreDrawListener(this)
                    val remaining = rv.computeVerticalScrollRange() -
                        rv.computeVerticalScrollExtent() - rv.computeVerticalScrollOffset()
                    val item = rv.layoutManager?.findViewByPosition(position)
                    val allowed = (item?.top ?: Int.MAX_VALUE) - rv.paddingTop
                    when {
                        // 顶部已到（或越过）列表顶部：本就该停
                        allowed <= 0 -> {
                            Log.d("TW", "capped top=${item?.top} h=${item?.height} rem=$remaining")
                            onCapped()
                        }
                        allowed < remaining -> {
                            rv.scrollBy(0, allowed)
                            onCapped()
                        }
                        else -> rv.scrollBy(0, remaining)
                    }
                    return true
                }
            },
        )
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
