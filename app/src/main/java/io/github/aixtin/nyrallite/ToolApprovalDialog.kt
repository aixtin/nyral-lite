package io.github.aixtin.nyrallite

import android.app.Activity
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.aixtin.nyrallite.contract.ApprovalDecision
import io.github.aixtin.nyrallite.contract.ToolApproval
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/**
 * [ToolApproval] 的界面对现：用系统对话框询问用户，挂起直到选择。
 *
 * 只持有前台 Activity 的弱引用；没有界面时返回 UNAVAILABLE，
 * 由安全插件按拒绝处理。界面不复制母体：三个主按钮，正文下方是
 * 「加入白名单/加入黑名单」两个列表项（名单由安全插件持久化）。
 *
 * 注意：appcompat 的 AlertDialog 在 setMessage 与 setItems 共存时会
 * 静默丢弃列表（setupContent 只在 message 为空时才把 ListView 换进
 * 层级），因此这里用自绘正文 + 列表项的自定义视图，而不是 setItems。
 */
class ToolApprovalDialog : ToolApproval {

    private var host: WeakReference<Activity>? = null

    /** 由 Activity 在 onCreate 时登记。 */
    fun attach(activity: Activity) {
        host = WeakReference(activity)
    }

    /** Activity 销毁时注销；只清自己，避免误伤新 Activity。 */
    fun detach(activity: Activity) {
        if (host?.get() === activity) host = null
    }

    override suspend fun ask(
        pluginId: String,
        tool: String,
        arguments: String,
    ): ApprovalDecision = withContext(Dispatchers.Main) {
        val activity = host?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
            ?: return@withContext ApprovalDecision.UNAVAILABLE

        suspendCancellableCoroutine { cont ->
            var settled = false
            fun settle(decision: ApprovalDecision) {
                if (settled) return
                settled = true
                if (cont.isActive) cont.resume(decision)
            }

            val source = sourceLabel(pluginId)
            val known = toolInfo[tool]
            val title = activity.getString(R.string.approval_title, known?.first ?: tool)
            val message = buildString {
                append(activity.getString(R.string.approval_tool_source, tool, source))
                known?.let { append('\n').append(it.second) }
                formatArgs(arguments)?.let {
                    append("\n\n").append(activity.getString(R.string.approval_args_header))
                        .append('\n').append(it)
                }
            }
            // 自定义视图的列表项点击后需手动关弹窗；先建 content 再建 dialog，
            // 用可变回调把 dismiss 接进去。
            var dismissDialog: () -> Unit = {}
            val dialog = MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setView(
                    buildContent(activity, message) { decision ->
                        settle(decision)
                        dismissDialog()
                    },
                )
                .setPositiveButton(R.string.approval_allow_once) { _, _ -> settle(ApprovalDecision.ALLOW_ONCE) }
                .setNeutralButton(R.string.approval_allow_session) { _, _ -> settle(ApprovalDecision.ALLOW_SESSION) }
                .setNegativeButton(R.string.approval_deny) { _, _ -> settle(ApprovalDecision.DENY) }
                .setOnCancelListener { settle(ApprovalDecision.DENY) }
                .create()
            dismissDialog = { dialog.dismiss() }

            dialog.setCanceledOnTouchOutside(false)
            dialog.setOnDismissListener { settle(ApprovalDecision.DENY) }
            try {
                dialog.show()
            } catch (e: Exception) {
                settle(ApprovalDecision.UNAVAILABLE)
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation { activity.runOnUiThread { dialog.dismiss() } }
        }
    }

    /** 正文 + 两个名单列表项；点击列表项即按对应决定结算。 */
    private fun buildContent(
        activity: Activity,
        message: CharSequence,
        onPick: (ApprovalDecision) -> Unit,
    ): View {
        val hpad = attrDimen(activity, androidx.appcompat.R.attr.dialogPreferredPadding)
        val vpad = activity.resources.getDimensionPixelOffset(
            androidx.appcompat.R.dimen.abc_dialog_padding_top_material,
        )
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(activity).apply {
                    text = message
                    setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Subhead)
                    setPadding(hpad, vpad, hpad, vpad / 2)
                },
            )
            addView(
                listItem(activity, activity.getString(R.string.approval_add_allowlist)) {
                    onPick(ApprovalDecision.ALLOW_ALWAYS)
                },
            )
            addView(
                listItem(activity, activity.getString(R.string.approval_add_denylist)) {
                    onPick(ApprovalDecision.DENY_ALWAYS)
                },
            )
        }
    }

    /** 单个列表项：48dp 触摸高度 + 水波纹背景，观感对齐系统列表项。 */
    private fun listItem(activity: Activity, label: String, onClick: () -> Unit): TextView {
        val hpad = attrDimen(activity, androidx.appcompat.R.attr.dialogPreferredPadding)
        val vpad = activity.resources.getDimensionPixelOffset(
            androidx.appcompat.R.dimen.abc_dialog_padding_top_material,
        )
        val touch = (48 * activity.resources.displayMetrics.density).toInt()
        val bg = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, bg, true)
        return TextView(activity).apply {
            text = label
            setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Subhead)
            gravity = Gravity.CENTER_VERTICAL
            minHeight = touch
            setPadding(hpad, vpad / 2, hpad, vpad / 2)
            if (bg.resourceId != 0) setBackgroundResource(bg.resourceId)
            setOnClickListener { onClick() }
        }
    }

    /** 解析主题属性里的尺寸值。 */
    private fun attrDimen(activity: Activity, attr: Int): Int {
        val tv = TypedValue()
        activity.theme.resolveAttribute(attr, tv, true)
        return TypedValue.complexToDimensionPixelSize(tv.data, activity.resources.displayMetrics)
    }

    private companion object {
        /** 来源标识转用户可读名称；未知来源原样展示。 */
        fun sourceLabel(id: String): String = when (id) {
            "builtin:system-tools" -> "系统工具"
            "builtin:device-tools" -> "设备能力"
            "builtin:memory" -> "记忆"
            "builtin:history" -> "历史检索"
            "mcp-client" -> "MCP 扩展"
            else -> id
        }

        /** 工具名转用户可读名称与一句说明；未收录的工具（如 MCP 扩展）原样展示名称。 */
        val toolInfo: Map<String, Pair<String, String>> = mapOf(
            "get_location" to Pair("获取位置", "获取你当前的地理位置（经纬度）。"),
            "read_clipboard" to Pair("读取剪贴板", "读取你手机剪贴板里的文本。剪贴板里可能有你复制过的密码、验证码等敏感内容，确认没有再允许。"),
            "write_clipboard" to Pair("写入剪贴板", "把一段文字写进你的剪贴板，之后你可以在任何 App 里粘贴。"),
            "send_notification" to Pair("发送通知", "以本 App 的名义发一条系统通知，会出现在通知栏。"),
            "dial_phone" to Pair("拨打电话", "打开系统拨号界面并填好号码，不会自动拨出，需要你自己按拨打。"),
            "open_browser" to Pair("打开网页", "调起浏览器打开一个网址。"),
            "share_text" to Pair("分享文字", "弹出系统分享面板，把一段文字分享到微信、QQ 等其他 App。"),
            "start_foreground_task" to Pair("启动后台任务", "启动一个带常驻通知的后台任务，可按设定间隔反复发通知提醒你。"),
            "stop_foreground_task" to Pair("停止后台任务", "停止一个正在运行的后台提醒任务。"),
            "remember" to Pair("写入记忆", "把一条信息存进你的长期记忆。"),
            "memory_search" to Pair("查询记忆", "检索你的长期记忆。"),
            "forget" to Pair("删除记忆", "按编号删除一条长期记忆。"),
            "history_search" to Pair("检索历史", "搜索你和助手的过往对话记录。"),
        )

        /** 参数 JSON 转可读行；解析失败回退原文截断。 */
        fun formatArgs(arguments: String): String? {
            val trimmed = arguments.trim()
            if (trimmed.isEmpty() || trimmed == "{}") return null
            val parsed = runCatching { JSONObject(trimmed) }.getOrNull()
                ?: return arguments.take(300)
            if (parsed.length() == 0) return null
            return parsed.keys().asSequence().joinToString("\n") { key ->
                val value = parsed.opt(key)?.toString() ?: ""
                if (value.length > 100) "$key：${value.take(100)}…" else "$key：$value"
            }
        }
    }
}
