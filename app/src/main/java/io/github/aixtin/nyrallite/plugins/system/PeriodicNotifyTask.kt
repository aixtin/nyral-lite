package io.github.aixtin.nyrallite.plugins.system

import io.github.aixtin.nyrallite.contract.ForegroundContract
import io.github.aixtin.nyrallite.contract.NotificationContract
import kotlinx.coroutines.delay

/**
 * 周期性发通知的 [ForegroundContract.Task] 实现。
 * 构造一次，传给 ForegroundContract.startTask 后宿主托管。
 * runOnce=true 时只发一次就退出前台，否则无限循环直到 stopTask 或被 cancel。
 */
internal class PeriodicNotifyTask(
    private val notifier: NotificationContract,
    private val intervalMin: Long,
    private val notifTitle: String,
    private val notifBody: String,
    private val runOnce: Boolean,
) : ForegroundContract.Task {

    override suspend fun run(params: Map<String, String>) {
        // 先立刻发一条，让用户看到任务已启动
        runCatching { notifier.send(notifTitle, "任务已启动：$notifBody") }
        if (runOnce) {
            delay(intervalMin * 60_000L)
            runCatching { notifier.send(notifTitle, notifBody) }
            return
        }
        while (true) {
            delay(intervalMin * 60_000L)
            runCatching { notifier.send(notifTitle, notifBody) }
        }
    }
}
