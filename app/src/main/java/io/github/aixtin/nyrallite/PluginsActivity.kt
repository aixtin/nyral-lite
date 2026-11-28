package io.github.aixtin.nyrallite

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.aixtin.nyrallite.contract.McpConfigStore
import io.github.aixtin.nyrallite.contract.SecuritySettingsStore
import io.github.aixtin.nyrallite.kernel.getOrNull
import io.github.aixtin.nyrallite.plugins.js.JsToolRepo
import io.github.aixtin.nyrallite.plugins.js.JsToolRunner
import io.github.aixtin.nyrallite.plugins.js.JsToolStore
import io.github.aixtin.nyrallite.plugins.wasm.WasmHttpFetcher
import io.github.aixtin.nyrallite.plugins.wasm.WasmPluginRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 插件页：内置插件状态 + 外部 Dex 插件的导入/启停 + 工具自测面板。
 * 自测面板见 [ToolTestPanel]，APK 导入见 [PluginApkImporter]，
 * WASM 导入见 [PluginWasmImporter]，JS 工具导入见 [JsToolImporter]。
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
        setupApkImportVisibility()
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
            { msg, isError -> updateDexStatus(msg, isError) },
            ::apkImportUnlocked,
            ::refreshDynamicSection,
        )
        val importer = PluginApkImporter(this, scope, ::updateDexStatus) {
            externalPanel.refresh()
        }
        findViewById<Button>(R.id.btn_import_apk).setOnClickListener { importer.launch() }
        // 说明文字与导入入口同进退：入口隐藏时说明一并隐藏，状态消息仍会临时显示
        findViewById<TextView>(R.id.dex_plugin_status).visibility =
            if (apkImportUnlocked()) View.VISIBLE else View.GONE
        val wasmImporter = PluginWasmImporter(this, scope) { msg, isError ->
            updateDexStatus(msg, isError)
            refreshWasmPlugins()
        }
        findViewById<Button>(R.id.btn_import_wasm).setOnClickListener { wasmImporter.launch() }
        val jsImporter = JsToolImporter(this, scope) { msg, isError ->
            updateDexStatus(msg, isError)
            refreshJsTools()
        }
        findViewById<Button>(R.id.btn_import_js).setOnClickListener { jsImporter.launch() }
        findViewById<TextView>(R.id.btn_unload_all).setOnClickListener { unloadAll(app) }
        externalPanel.refresh()
        refreshWasmPlugins()
        refreshJsTools()

        buildPluginCards()
    }

    /**
     * APK 导入入口默认隐藏：连点标题 15 次（3 秒内）解锁，单向持久生效。
     * 已安装的 APK 插件不受影响，照常展示与加载；WASM 导入入口不受此控制。
     */
    /**
     * 动态插件区整体显隐：入口未解锁且从未装过 APK 插件时整区隐藏，
     * 避免普通用户看到一个无法操作的空区。装过插件（即使已停用）则保留展示。
     */
    private fun refreshDynamicSection() {
        val show = apkImportUnlocked() ||
            (application as App).externalPlugins.all().isNotEmpty()
        val visibility = if (show) View.VISIBLE else View.GONE
        findViewById<View>(R.id.dynamic_header).visibility = visibility
        findViewById<View>(R.id.btn_unload_all).visibility = visibility
        findViewById<View>(R.id.external_plugin_list).visibility = visibility
        if (!show) findViewById<View>(R.id.dex_plugin_status).visibility = View.GONE
    }

    private fun apkImportUnlocked(): Boolean =
        getSharedPreferences(PREF_PLUGINS_DEV, MODE_PRIVATE)
            .getBoolean(KEY_APK_IMPORT_UNLOCKED, false)

    private fun setupApkImportVisibility() {
        val apkImport = findViewById<Button>(R.id.btn_import_apk)
        apkImport.visibility =
            if (apkImportUnlocked()) View.VISIBLE else View.GONE
        var tapCount = 0
        var tapStart = 0L
        findViewById<TextView>(R.id.plugins_title_text).setOnClickListener {
            val now = SystemClock.elapsedRealtime()
            if (now - tapStart > TAP_WINDOW_MS) {
                tapCount = 0
                tapStart = now
            }
            tapCount++
            if (tapCount >= UNLOCK_TAPS) {
                getSharedPreferences(PREF_PLUGINS_DEV, MODE_PRIVATE)
                    .edit().putBoolean(KEY_APK_IMPORT_UNLOCKED, true).apply()
                apkImport.visibility = View.VISIBLE
                findViewById<TextView>(R.id.dex_plugin_status).visibility = View.VISIBLE
                refreshDynamicSection()
                tapCount = 0
            }
        }
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
                alpha = 0.5f
                textSize = 12f
            })
            return
        }
        val rows = ExternalPluginRow(this)
        for (id in ids) {
            container.addView(rows.wasm(id) { removeWasmPlugin(id) })
        }
    }

    private fun removeWasmPlugin(id: String) {
        val ok = WasmPluginRepo.uninstall(this, id)
        updateDexStatus(if (ok) "已移除 WASM 插件：$id" else "移除失败：$id 的插件文件无法删除", !ok)
        refreshWasmPlugins()
    }

    /** 刷新 JS 工具列表：展示已安装项，支持运行（按需执行）与移除。 */
    private fun refreshJsTools() {
        val container = findViewById<LinearLayout>(R.id.js_tool_list) ?: return
        container.removeAllViews()
        val ids = JsToolRepo.list(this)
        if (ids.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "暂无 JS 工具。点「导入 JS」选择 .js 文件。"
                setTextColor(ContextCompat.getColor(this@PluginsActivity, R.color.ai_text))
                alpha = 0.5f
                textSize = 12f
            })
            return
        }
        val rows = ExternalPluginRow(this)
        for (id in ids) {
            container.addView(
                rows.jsTool(id, onRun = { runJsTool(id) }, onRemove = { removeJsTool(id) }),
            )
        }
    }

    /** 运行 JS 工具：IO 线程执行（含网络），结果弹窗展示。 */
    private fun runJsTool(id: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                JsToolRunner.run(this@PluginsActivity, id) { url -> WasmHttpFetcher.fetch(url) }
            }
            MaterialAlertDialogBuilder(this@PluginsActivity)
                .setTitle("JS 工具：$id")
                .setMessage(if (result.isError) "失败：${result.content}" else result.content)
                .setPositiveButton("关闭", null)
                .show()
        }
    }

    private fun removeJsTool(id: String) {
        val ok = JsToolRepo.uninstall(this, id)
        JsToolStore(this).remove(id)
        updateDexStatus(if (ok) "已移除 JS 工具：$id" else "移除失败：$id 的工具文件无法删除", !ok)
        refreshJsTools()
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
                visibility = View.VISIBLE
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
        const val PREF_PLUGINS_DEV = "plugins_dev"
        const val KEY_APK_IMPORT_UNLOCKED = "apk_import_unlocked"
        const val UNLOCK_TAPS = 15
        const val TAP_WINDOW_MS = 3_000L
    }
}
