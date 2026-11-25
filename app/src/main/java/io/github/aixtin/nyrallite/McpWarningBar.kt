package io.github.aixtin.nyrallite

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 标题栏下方的工具来源告警条：MCP 等来源拉取失败时常驻显示原因，
 * 点击直接进 MCP 设置页；恢复后自动收起。
 */
internal class McpWarningBar private constructor(
    private val activity: AppCompatActivity,
    private val view: TextView,
) {
    fun show(message: String?) {
        if (message == null) {
            view.visibility = View.GONE
            return
        }
        view.text = activity.getString(R.string.mcp_tools_failed, message.take(80))
        view.visibility = View.VISIBLE
    }

    companion object {
        fun attach(activity: AppCompatActivity): McpWarningBar {
            val view = activity.findViewById<TextView>(R.id.mcp_warning)
            view.setOnClickListener {
                activity.startActivity(Intent(activity, McpConfigActivity::class.java))
            }
            return McpWarningBar(activity, view)
        }
    }
}
