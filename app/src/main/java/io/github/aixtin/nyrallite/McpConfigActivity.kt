package io.github.aixtin.nyrallite

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.aixtin.nyrallite.contract.McpProbe
import io.github.aixtin.nyrallite.kernel.getOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** MCP 设置页：服务地址、自定义请求头与连接测试。 */
class McpConfigActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mcp_config)
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, maxOf(bars.bottom, ime.bottom))
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        val form = McpForm(this)
        form.load()

        val btnTest = findViewById<Button>(R.id.btn_test_mcp)
        val tvStatus = findViewById<TextView>(R.id.tv_mcp_status)
        val probe = kernel.services.getOrNull<McpProbe>()
        btnTest.setOnClickListener { testMcp(probe, btnTest, tvStatus, form) }

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            form.save()
            finish()
        }
    }

    /** 测试当前草稿的 MCP 连接，不提前落盘。 */
    private fun testMcp(probe: McpProbe?, btn: Button, status: TextView, form: McpForm) {
        if (probe == null) {
            status.setText(R.string.cfg_mcp_probe_unavailable)
            return
        }
        status.setText(R.string.cfg_mcp_testing)
        btn.isEnabled = false
        val draft = form.draft()
        scope.launch {
            val result = probe.probe(draft)
            btn.isEnabled = true
            val fail = result.error?.let { error ->
                getString(R.string.cfg_mcp_fail, error.take(200)) +
                    if (draft.serverUrl.trimEnd('/').endsWith("/sse")) {
                        "\n" + getString(R.string.cfg_mcp_sse_hint)
                    } else {
                        ""
                    }
            } ?: getString(R.string.cfg_mcp_ok, result.tools.size)
            status.text = fail
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
