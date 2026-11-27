package io.github.aixtin.nyrallite.plugins.system

import io.github.aixtin.nyrallite.contract.ForegroundContract
import io.github.aixtin.nyrallite.contract.IntentContract
import io.github.aixtin.nyrallite.contract.NotificationContract
import io.github.aixtin.nyrallite.contract.ToolResult
import io.github.aixtin.nyrallite.contract.ToolSource
import io.github.aixtin.nyrallite.contract.ToolSpec

/**
 * 系统原生工具来源：把 [NotificationContract] / [IntentContract] / [ForegroundContract]
 * 包装成模型可调用的 ToolSource。
 *
 * 和 MCP 远程工具平级，由 MultiToolRegistry 聚合后对 ChatTransport 统一暴露。
 * 工具声明见 [SystemToolSpecs]；参数是 JSON 字符串，解析后调用契约方法。
 */
class SystemToolSource(
    private val notifier: NotificationContract,
    private val dispatcher: IntentContract,
    private val foreground: ForegroundContract,
) : ToolSource {

    override val sourceId: String = "builtin:system-tools"

    override suspend fun list(): List<ToolSpec> = SystemToolSpecs.ALL

    override suspend fun callOrNull(name: String, argumentsJson: String): ToolResult? {
        return when (name) {
            "send_notification" -> callSendNotification(argumentsJson)
            "dial_phone" -> callDialPhone(argumentsJson)
            "open_browser" -> callOpenBrowser(argumentsJson)
            "share_text" -> callShareText(argumentsJson)
            "start_foreground_task" -> callStartForeground(argumentsJson)
            "stop_foreground_task" -> callStopForeground(argumentsJson)
            else -> null
        }
    }

    private fun callSendNotification(json: String): ToolResult = runCatching {
        val params = parseToolArgs(json)
        notifier.send(
            title = params["title"] ?: "Nyral Lite",
            body = params["body"] ?: "",
            channelId = params["channelId"] ?: "nyral_generic",
        )
        ToolResult("通知已发送：${params["title"] ?: "Nyral Lite"}")
    }.getOrElse { ToolResult("发送通知失败: ${it.message}", isError = true) }

    private fun callDialPhone(json: String): ToolResult = runCatching {
        val phone = parseToolArgs(json)["phone"]
            ?: return ToolResult("缺少参数 phone", isError = true)
        val ok = dispatcher.dial(phone)
        if (ok) ToolResult("已打开拨号器: $phone")
        else ToolResult("无法打开拨号器", isError = true)
    }.getOrElse { ToolResult("拨号失败: ${it.message}", isError = true) }

    private fun callOpenBrowser(json: String): ToolResult = runCatching {
        val url = parseToolArgs(json)["url"]
            ?: return ToolResult("缺少参数 url", isError = true)
        val ok = dispatcher.openBrowser(url)
        if (ok) ToolResult("已打开浏览器: $url")
        else ToolResult("无法打开浏览器", isError = true)
    }.getOrElse { ToolResult("打开浏览器失败: ${it.message}", isError = true) }

    private fun callShareText(json: String): ToolResult = runCatching {
        val params = parseToolArgs(json)
        val ok = dispatcher.shareText(
            text = params["text"] ?: "",
            title = params["title"] ?: "分享给",
        )
        if (ok) ToolResult("已打开分享选择器")
        else ToolResult("无法打开分享选择器", isError = true)
    }.getOrElse { ToolResult("分享失败: ${it.message}", isError = true) }

    /**
     * 启动周期性前台任务：每隔 interval_minutes 发一条通知。
     * 任务实例在这里构造，通过 ForegroundContract 交给宿主 ForegroundService 托管。
     */
    private fun callStartForeground(json: String): ToolResult = runCatching {
        val params = parseToolArgs(json)
        val taskId = params["taskId"]
            ?: return@runCatching ToolResult("缺少参数 taskId", isError = true)
        val intervalMin = (params["interval_minutes"] ?: "15").toLongOrNull()
            ?: return@runCatching ToolResult("interval_minutes 必须是整数", isError = true)
        val notifTitle = params["notification_title"] ?: "Nyral Lite 提醒"
        val notifBody = params["notification_body"] ?: "定时提醒"
        val fgTitle = params["title"] ?: "Nyral Lite 后台任务"
        val fgBody = params["body"] ?: "每 ${intervalMin} 分钟提醒一次"
        val runOnce = (params["run_once"] ?: "false").toBoolean()

        if (foreground.runningTaskIds().contains(taskId)) {
            return@runCatching ToolResult("任务 $taskId 已在运行中", isError = true)
        }

        val task = PeriodicNotifyTask(
            notifier = notifier,
            intervalMin = intervalMin.coerceAtLeast(1),
            notifTitle = notifTitle,
            notifBody = notifBody,
            runOnce = runOnce,
        )
        val ok = foreground.startTask(
            taskId = taskId,
            task = task,
            title = fgTitle,
            body = fgBody,
        )
        if (ok) ToolResult("任务已启动：$taskId（每 ${intervalMin} 分钟）")
        else ToolResult("任务启动失败：$taskId", isError = true)
    }.getOrElse { ToolResult("启动前台任务失败: ${it.message}", isError = true) }

    private fun callStopForeground(json: String): ToolResult = runCatching {
        val params = parseToolArgs(json)
        val taskId = params["taskId"]
            ?: return ToolResult("缺少参数 taskId", isError = true)
        val ids = foreground.runningTaskIds()
        if (taskId !in ids) {
            return ToolResult("任务 $taskId 不在运行中", isError = true)
        }
        val ok = foreground.stopTask(taskId)
        if (ok) ToolResult("任务已停止：$taskId")
        else ToolResult("停止任务失败：$taskId", isError = true)
    }.getOrElse { ToolResult("停止前台任务失败: ${it.message}", isError = true) }
}
