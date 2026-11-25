package io.github.aixtin.nyrallite

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.aixtin.nyrallite.contract.ApiConfigStore
import io.github.aixtin.nyrallite.contract.McpConfigStore
import io.github.aixtin.nyrallite.contract.SecurityMode
import io.github.aixtin.nyrallite.contract.SecuritySettingsStore
import io.github.aixtin.nyrallite.kernel.get

/** 设置主页：模型、MCP、安全门禁三个入口，各自独立成页。 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, bars.bottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.entry_model).setOnClickListener {
            startActivity(Intent(this, ModelConfigActivity::class.java))
        }
        findViewById<View>(R.id.entry_mcp).setOnClickListener {
            startActivity(Intent(this, McpConfigActivity::class.java))
        }
        findViewById<View>(R.id.entry_security).setOnClickListener {
            startActivity(Intent(this, SecurityConfigActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        renderSummaries()
    }

    /** 三个入口的当前值摘要，从子页返回后自动刷新。 */
    private fun renderSummaries() {
        val api = kernel.services.get<ApiConfigStore>().load()
        val mcp = kernel.services.get<McpConfigStore>().load()
        val security = kernel.services.get<SecuritySettingsStore>().load()

        findViewById<TextView>(R.id.summary_model).text =
            if (api.model.isBlank() && api.baseUrl.isBlank()) {
                getString(R.string.settings_unset)
            } else {
                getString(R.string.settings_model_on, api.model.ifBlank { "—" }, api.baseUrl)
            }
        findViewById<TextView>(R.id.summary_mcp).text =
            if (mcp.isConfigured) {
                getString(R.string.settings_mcp_on, mcp.serverUrl.take(40))
            } else {
                getString(R.string.settings_unset)
            }
        findViewById<TextView>(R.id.summary_security).text =
            getString(R.string.settings_security_mode, getString(modeLabel(security.mode)))
    }

    private fun modeLabel(mode: SecurityMode): Int = when (mode) {
        SecurityMode.OFF -> R.string.cfg_security_off
        SecurityMode.AUTO -> R.string.cfg_security_auto
        SecurityMode.STRICT -> R.string.cfg_security_strict
    }
}
