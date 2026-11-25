package io.github.aixtin.nyrallite

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import io.github.aixtin.nyrallite.contract.ForegroundContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 通用前台服务：宿主预埋在 AndroidManifest，所有插件长任务共用一个 Service。
 *
 * 插件不直接接触 Service，只调 [ForegroundContract]。Service 内部维护
 * runningTasks Map，收到 start/stop 指令后路由到对应任务。
 *
 * 全部任务停止后自动 stopSelf()，不会白占前台。
 */
class NyralForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val runningTasks = LinkedHashMap<String, RunningTask>()
    private val notifications = ForegroundNotifications(this)

    override fun onCreate() {
        super.onCreate()
        startForeground(
            ForegroundNotifications.NOTIF_ID,
            notifications.build("Nyral Lite", "正在运行"),
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Nyral Lite"
                val body = intent.getStringExtra(EXTRA_BODY) ?: "正在运行"
                registerAndRun(taskId, title, body)
            }
            ACTION_STOP -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                stopTask(taskId)
            }
            ACTION_STOP_ALL -> {
                stopAll()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun registerAndRun(taskId: String, title: String, body: String) {
        if (runningTasks.containsKey(taskId)) return
        val app = application as? App ?: return
        val dispatcher = app.kernel.services.getOrNull(ForegroundDispatcher::class) ?: return
        val task = dispatcher.consumeTask(taskId) ?: return

        notifications.update(title, body)
        val job = scope.launch {
            runCatching { task.run(emptyMap()) }
                .onFailure { it.printStackTrace() }
            // 无论成功失败都通知 dispatcher 清 runningIds
            dispatcher.notifyTaskDone(taskId)
            synchronized(runningTasks) { runningTasks.remove(taskId) }
            if (runningTasks.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        synchronized(runningTasks) {
            runningTasks[taskId] = RunningTask(task, job)
        }
    }

    private fun stopTask(taskId: String) {
        val rt = synchronized(runningTasks) { runningTasks.remove(taskId) } ?: return
        rt.job.cancel()
        // 通知 dispatcher 清 runningIds，让 foreground.runningTaskIds() 能及时反映
        val app = application as? App
        app?.kernel?.services?.getOrNull(ForegroundDispatcher::class)?.notifyTaskDone(taskId)
        if (runningTasks.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopAll() {
        val ids = synchronized(runningTasks) {
            val ids = runningTasks.keys.toList()
            runningTasks.values.forEach { it.job.cancel() }
            runningTasks.clear()
            ids
        }
        val app = application as? App
        val dispatcher = app?.kernel?.services?.getOrNull(ForegroundDispatcher::class)
        ids.forEach { dispatcher?.notifyTaskDone(it) }
    }

    override fun onDestroy() {
        stopAll()
        scope.cancel()
        super.onDestroy()
    }

    companion object {        const val ACTION_START = "io.github.aixtin.nyrallite.action.START_TASK"
        const val ACTION_STOP = "io.github.aixtin.nyrallite.action.STOP_TASK"
        const val ACTION_STOP_ALL = "io.github.aixtin.nyrallite.action.STOP_ALL_TASKS"

        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"

        fun startIntent(
            context: Context,
            taskId: String,
            title: String,
            body: String,
        ): Intent = Intent(context, NyralForegroundService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
        }

        fun stopIntent(context: Context, taskId: String): Intent =
            Intent(context, NyralForegroundService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_TASK_ID, taskId)
            }

        fun stopAllIntent(context: Context): Intent =
            Intent(context, NyralForegroundService::class.java).apply {
                action = ACTION_STOP_ALL
            }
    }
}

private data class RunningTask(
    val task: ForegroundContract.Task,
    val job: kotlinx.coroutines.Job,
)
