package io.github.aixtin.nyrallite

import android.os.Handler

/** 思考中的省略号动画：每 600ms 循环 ('.' / '..' / '...' / 空)。 */
internal class ThinkingAnimator(
    private val handler: Handler,
    private val onTick: (dots: Int) -> Unit,
    /** 是否继续下一帧：由调用方判断消息是否仍在流式。 */
    private val isActive: () -> Boolean,
) {
    private var count = 0
    private var runnable: Runnable? = null

    fun start() {
        if (runnable != null) return
        val task = object : Runnable {
            override fun run() {
                if (!isActive()) {
                    runnable = null
                    return
                }
                count = (count + 1) % 4
                onTick(
                    when (count) {
                        0 -> 1
                        1 -> 2
                        2 -> 3
                        else -> 0
                    },
                )
                handler.postDelayed(this, 600)
            }
        }
        runnable = task
        handler.postDelayed(task, 600)
    }

    fun stop() {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }
}
