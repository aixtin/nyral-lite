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
            button.animate().alpha(0f).setDuration(200).withEndAction {
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
                handler.postDelayed(hideRunnable, 1200)
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
            button.animate().alpha(1f).setDuration(150).start()
        }
        handler.postDelayed(hideRunnable, 1500)
    }
}
