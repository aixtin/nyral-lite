package io.github.aixtin.nyrallite

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.aixtin.nyrallite.contract.McpConfigStore
import io.github.aixtin.nyrallite.contract.SecuritySettingsStore
import io.github.aixtin.nyrallite.kernel.getOrNull
import io.github.aixtin.nyrallite.plugins.wasm.WasmPluginRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 插件页：内置插件状态 + 外部 Dex 插件的导入/启停 + 工具自测面板。
 * 自测面板见 [ToolTestPanel]，APK 导入见 [PluginApkImporter]，WASM 导入见 [PluginWasmImporter]。
 */
class PluginsActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var externalPanel: ExternalPluginPanel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plugins)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, bars.bottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_tool_test).setOnClickListener {
            startActivity(Intent(this, ToolTestActivity::class.java))
        }

        // 外部 Dex 插件区：导入 / 全部移除 / 启用开关列表
        val app = application as App
        externalPanel = ExternalPluginPanel(
            this,
            app,
            findViewById(R.id.external_plugin_list),
            scope,
        ) { msg, isError -> updateDexStatus(msg, isError) }
        val importer = PluginApkImporter(this, scope, ::updateDexStatus) {
            externalPanel.refresh()
        }
        findViewById<Button>(R.id.btn_import_apk).setOnClickListener { importer.launch() }
        val wasmImporter = PluginWasmImporter(this, scope) { msg, isError ->
            updateDexStatus(msg, isError)
            refreshWasmPlugins()
        }
        findViewById<Button>(R.id.btn_import_wasm).setOnClickListener { wasmImporter.launch() }
        findViewById<TextView>(R.id.btn_unload_all).setOnClickListener { unloadAll(app) }
        externalPanel.refresh()
        refreshWasmPlugins()

        buildPluginCards()
    }

    /** 刷新 WASM 插件列表：展示已安装项，支持移除（删文件 + 吊销信任）。 */
    private fun refreshWasmPlugins() {
        val container = findViewById<LinearLayout>(R.id.wasm_plugin_list) ?: return
        container.removeAllViews()
        val ids = WasmPluginRepo.list(this)
        if (ids.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "暂无 WASM 插件。点「导入 WASM」选择 .wasm 文件。"
                setTextColor(ContextCompat.getColor(this@PluginsActivity, R.color.ai_text))
                alpha = 0.6f
                textSize = 12f
            })
            return
        }
        for (id in ids) {
            container.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 12, 0, 12)
                addView(TextView(this@PluginsActivity).apply {
                    text = id
                    setTextColor(ContextCompat.getColor(this@PluginsActivity, R.color.ai_text))
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(TextView(this@PluginsActivity).apply {
                    text = "移除"
                    setTextColor(ContextCompat.getColor(this@PluginsActivity, R.color.tool_fail))
                    textSize = 13f
                    background = ContextCompat.getDrawable(this@PluginsActivity, R.drawable.bg_bar_item)
                    gravity = android.view.Gravity.CENTER
                    minWidth = 64.dp
                    val pad = 8.dp
                    setPadding(pad, pad / 2, pad, pad / 2)
                    setOnClickListener { removeWasmPlugin(id) }
                })
            })
        }
    }

    private fun removeWasmPlugin(id: String) {
        val ok = WasmPluginRepo.uninstall(this, id)
        updateDexStatus(if (ok) "已移除 WASM 插件：$id" else "移除失败：$id 的插件文件无法删除", !ok)
        refreshWasmPlugins()
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    /** 移除全部外部插件：卸载内核侧与磁盘 APK，再清记录。 */
    private fun unloadAll(app: App) {
        for (record in app.externalPlugins.all()) {
            kernel.uninstall(record.id)
            app.dexLoader.unload(record.id, deleteApk = true)
            app.externalPlugins.remove(record.id)
        }
        updateDexStatus("已移除全部外部插件", false)
        externalPanel.refresh()
    }

    /** 内置插件状态卡片。 */
    private fun buildPluginCards() {
        val container = findViewById<LinearLayout>(R.id.plugin_list)
        val gate = kernel.services.getOrNull<SecuritySettingsStore>()?.load()
        val mcpConfig = kernel.services.getOrNull<McpConfigStore>()?.load()
        for (manifest in (application as App).pluginManifests) {
            val security = manifest.id == ID_SECURITY
            val mcpClient = manifest.id == ID_MCP
            container.addView(
                PluginCardView(
                    context = this,
                    manifest = manifest,
                    installed = kernel.isInstalled(manifest.id),
                    gateMode = if (security) gate?.mode else null,
                    extraLine = if (mcpClient) {
                        if (mcpConfig?.isConfigured == true) {
                            getString(R.string.plugins_mcp_configured, mcpConfig.serverUrl.take(48))
                        } else {
                            getString(R.string.plugins_mcp_unconfigured)
                        }
                    } else {
                        null
                    },
                    openLabelRes = if (mcpClient) {
                        R.string.plugins_open_config_mcp
                    } else {
                        R.string.plugins_open_config
                    },
                    onOpenConfig = when {
                        security -> { { startActivity(Intent(this, SecurityConfigActivity::class.java)) } }
                        mcpClient -> { { startActivity(Intent(this, McpConfigActivity::class.java)) } }
                        else -> null
                    },
                ),
            )
        }
    }

    /** 更新 Dex 插件状态标签（动态插件区域）。 */
    private fun updateDexStatus(msg: String, isError: Boolean = false) {
        runOnUiThread {
            findViewById<TextView>(R.id.dex_plugin_status)?.apply {
                text = msg
                setTextColor(
                    ContextCompat.getColor(
                        this@PluginsActivity,
                        if (isError) R.color.tool_fail else R.color.ai_text,
                    ),
                )
            }
        }
    }

    private companion object {
        const val ID_SECURITY = "security-policy"
        const val ID_MCP = "mcp-client"
    }
}
