package io.github.aixtin.nyrallite

import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.aixtin.nyrallite.contract.ForegroundContract

/**
 * [ForegroundContract] 的宿主实现：插件调 startTask → 这里拉前台 Service → 托管任务。
 *
 * 任务实例存在这个全局 Map 里（Service 和 ForegroundDispatcher 同进程），
 * Service 通过 consumeTask(taskId) 取出后执行。这样避开了 Intent 不能直接
 * put 自定义 Kotlin 对象的问题。
 */
class ForegroundDispatcher(
    private val appContext: Context,
) : ForegroundContract {

    private val pending = LinkedHashMap<String, ForegroundContract.Task>()
    private val runningIds = LinkedHashSet<String>()

    /** NyralForegroundService 启动时调这个取任务（取完即移除，避免泄漏）。 */
    fun consumeTask(taskId: String): ForegroundContract.Task? {
        // 注意：Service 里用 runBlocking 或同步取（Service.onCreate 在主线程）
        // 这里用 synchronized 比 Mutex 方便
        synchronized(pending) {
            val t = pending.remove(taskId)
            if (t != null) runningIds.add(taskId)
            return t
        }
    }

    fun notifyTaskDone(taskId: String) {
        synchronized(runningIds) { runningIds.remove(taskId) }
    }

    override fun startTask(
        taskId: String,
        task: ForegroundContract.Task,
        params: Map<String, String>,
        title: String,
        body: String,
    ): Boolean {
        // 先存起来，Service 启动后从这里取
        synchronized(pending) {
            if (pending.containsKey(taskId) || runningIds.contains(taskId)) {
                return false
            }
            pending[taskId] = task
        }
        return tryStartService(title, body, taskId)
    }

    override fun stopTask(taskId: String): Boolean {
        synchronized(pending) { pending.remove(taskId) }
        synchronized(runningIds) { runningIds.remove(taskId) }
        val intent = NyralForegroundService.stopIntent(appContext, taskId)
        runCatching { appContext.startService(intent) }
        return true
    }

    override fun runningTaskIds(): List<String> {
        val ids = ArrayList<String>()
        synchronized(runningIds) { ids.addAll(runningIds) }
        synchronized(pending) { ids.addAll(pending.keys) }
        return ids
    }

    private fun tryStartService(title: String, body: String, taskId: String): Boolean {
        val intent = NyralForegroundService.startIntent(appContext, taskId, title, body)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            true
        }.getOrElse {
            // 启动失败（比如 Android 12+ 限制）时把 pending 里的 task 清掉
            synchronized(pending) { pending.remove(taskId) }
            false
        }
    }
}
