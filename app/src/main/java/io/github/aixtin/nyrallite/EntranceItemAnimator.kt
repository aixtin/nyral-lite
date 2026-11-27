package io.github.aixtin.nyrallite

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

/**
 * 消息入场动效：新条目轻微上浮 + 淡入，克制耐看。
 *
 * 只接管新增（animateAdd）路径，动画自管并在结束时回报 add finished；
 * 移动、移除、变更仍走系统默认，避免影响流式渲染与历史恢复的既有表现。
 * 历史整页恢复走 notifyDataSetChanged，不触发新增动画，不会整屏飘。
 */
internal class EntranceItemAnimator : DefaultItemAnimator() {

    private val interpolator = DecelerateInterpolator(1.6f)

    override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
        val view = holder.itemView
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = 12f * view.resources.displayMetrics.density
        dispatchAddStarting(holder)
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .setInterpolator(interpolator)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.animate().setListener(null)
                    view.translationY = 0f
                    view.alpha = 1f
                    dispatchAddFinished(holder)
                }
            })
        return true
    }
}
