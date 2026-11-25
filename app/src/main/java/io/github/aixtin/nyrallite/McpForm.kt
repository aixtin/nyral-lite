package io.github.aixtin.nyrallite

import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import io.github.aixtin.nyrallite.contract.McpConfig
import io.github.aixtin.nyrallite.contract.McpConfigStore
import io.github.aixtin.nyrallite.kernel.get

/** MCP 表单：服务地址与自定义请求头。 */
internal class McpForm(private val activity: AppCompatActivity) {

    private val inMcp = activity.findViewById<EditText>(R.id.in_mcp_url)
    private val inMcpHeader = activity.findViewById<EditText>(R.id.in_mcp_header)
    private val inMcpToken = activity.findViewById<EditText>(R.id.in_mcp_token)
    private val store = activity.kernel.services.get<McpConfigStore>()

    fun load() {
        val config = store.load()
        inMcp.setText(config.serverUrl)
        inMcpHeader.setText(config.headerName)
        inMcpToken.setText(config.headerValue)
    }

    /** 当前输入框里的草稿，测试连接与保存共用。 */
    fun draft() = McpConfig(
        serverUrl = inMcp.text.toString().trim(),
        headerName = inMcpHeader.text.toString().trim(),
        headerValue = inMcpToken.text.toString().trim(),
    )

    fun save() = store.save(draft())
}
