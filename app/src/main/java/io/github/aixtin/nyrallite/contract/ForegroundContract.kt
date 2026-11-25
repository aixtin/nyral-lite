package io.github.aixtin.nyrallite.contract

/**
 * 前台服务托管契约：让插件能跑长任务（定位监听、定时、剪贴板轮询……），
 * 由宿主的 [ForegroundService] 统一托管生命周期，插件不直接 import Android ForegroundService API。
 *
 * 为什么契约层不暴露 ForegroundService？
 *   - Android 12+ 限制 ForegroundService 启动类型（前台才能启动 / 启动类型声明），
 *     宿主预埋一个通用 Service（Manifest 静态声明），插件只调契约，避开这些坑。
 *   - 插件不需要声明 Android 组件权限。
 *
 * [Task] 是纯接口，不 import android.*——宿主实现 NyralForegroundService 时自己处理。
 */
interface ForegroundContract {

    /** 长任务：插件注册后，宿主在 ForegroundService 里调用它。 */
    interface Task {
        /**
         * 宿主给任务的参数（key-value 字符串）。
         * 比如 "reminder_interval_minutes" → "15" 表示 15 分钟提醒一次。
         */
        suspend fun run(params: Map<String, String>)
    }

    /**
     * 启动一个长任务，宿主会拉起 ForegroundService 并开始执行。
     *
     * @param taskId  唯一 ID（用于后续 stopTask）
     * @param task    任务实例（插件自己实现）
     * @param params  任务参数（透传给 Task.run）
     * @param title   前台服务的通知标题（用户能看到的"正在运行的任务"）
     * @param body    前台服务的通知正文
     * @return 是否成功启动（false 表示 ForegroundService 启动失败或 taskId 已在运行）
     */
    fun startTask(
        taskId: String,
        task: Task,
        params: Map<String, String> = emptyMap(),
        title: String,
        body: String,
    ): Boolean

    /** 停止指定 taskId 的长任务（前台通知会自动消失）。 */
    fun stopTask(taskId: String): Boolean

    /** 列出当前正在运行的 taskId。 */
    fun runningTaskIds(): List<String>
}
