package io.github.aixtin.nyrallite

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

/**
 * 回到底部按钮：
 *  - 仅在用户手指拖动且不在底部时出现（程序性滚动不弹）
 *  - 停手 / 滚到底后自动淡出
 *  - 同时把"是否滚动中"上报给 [ChatController]，用于暂停流式渲染
 */
class ScrollToBottomButton(
    private val rv: RecyclerView,
    private val button: ImageView,
    private val onScrollStateChanged: (Int) -> Unit,
    private val onPinToBottom: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())

    private val hideRunnable = Runnable {
        if (button.visibility == View.VISIBLE) {
            button.animate().alpha(0f).setDuration(FADE_OUT_MS).withEndAction {
                button.visibility = View.GONE
            }.start()
        }
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (!recyclerView.canScrollVertically(1)) {
                // 已到底: 立即收起
                handler.removeCallbacks(hideRunnable)
                hideRunnable.run()
                return
            }
            if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
                show()
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            // 手势/惯性滚动中冻结流式渲染；把原始状态传出去，由控制器区分拖动与自动滑动
            onScrollStateChanged(newState)
            if (newState == RecyclerView.SCROLL_STATE_IDLE && button.visibility == View.VISIBLE) {
                handler.removeCallbacks(hideRunnable)
                handler.postDelayed(hideRunnable, AUTO_HIDE_AFTER_IDLE_MS)
            }
        }
    }

    init {
        button.setOnClickListener {
            // 瞬时贴底, 不用 smoothScrollToPosition(超高条目会吸到条目顶部)
            onPinToBottom()
            handler.removeCallbacks(hideRunnable)
            hideRunnable.run()
        }
        rv.addOnScrollListener(scrollListener)
    }

    fun dispose() {
        rv.removeOnScrollListener(scrollListener)
        handler.removeCallbacks(hideRunnable)
    }

    private fun show() {
        handler.removeCallbacks(hideRunnable)
        if (button.visibility != View.VISIBLE) {
            button.visibility = View.VISIBLE
            button.alpha = 0f
            button.animate().alpha(1f).setDuration(FADE_IN_MS).start()
        }
        handler.postDelayed(hideRunnable, AUTO_HIDE_AFTER_SHOW_MS)
    }

    private companion object {
        /** 显示/隐藏动画时长（毫秒）。 */
        const val FADE_IN_MS: Long = 150L
        const val FADE_OUT_MS: Long = 200L
        /** 从手势停止到自动隐藏的延迟（毫秒）。 */
        const val AUTO_HIDE_AFTER_IDLE_MS: Long = 1_200L
        /** 显示后到自动隐藏的延迟（毫秒）。 */
        const val AUTO_HIDE_AFTER_SHOW_MS: Long = 1_500L
    }
}
