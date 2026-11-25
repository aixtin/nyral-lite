package io.github.aixtin.nyrallite

import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import io.github.aixtin.nyrallite.plugins.dex.ExternalPluginRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 外部 Dex 插件列表：展示持久化记录，支持启用/禁用与移除。
 *
 * 启用 = 加载 APK 并装入 Kernel；禁用 = 从 Kernel 卸载但保留 APK；
 * 移除 = 卸载 + 删 APK + 删记录。所有变更同步写入 ExternalPluginStore，
 * 重启后由 App.restoreExternalPlugins() 恢复已启用项。
 */
class ExternalPluginPanel(
    private val activity: AppCompatActivity,
    private val app: App,
    private val container: LinearLayout,
    private val scope: CoroutineScope,
    private val onStatus: (String, Boolean) -> Unit,
) {
    private val rows = ExternalPluginRow(activity, ::remove, ::toggle)

    fun refresh() {
        container.removeAllViews()
        val records = app.externalPlugins.all().sortedBy { it.installedAt }
        if (records.isEmpty()) {
            container.addView(rows.hint("暂无外部插件。点「导入 APK」选择插件包，第三方签名会先弹窗确认。"))
            return
        }
        for (record in records) {
            val installed = app.kernel.isInstalled(record.id)
            // 只有已安装（运行中）的插件才能拿到面板描述；未实现 ConfigurablePlugin 时显示按钮也没意义
            val spec = if (installed) app.kernel.panelOf(record.id) else null
            val configure: (() -> Unit)? = spec?.let { s ->
                {
                    PluginPanelDialog.show(activity, record.id, s, app.kernel.configOf(record.id)) {
                        onStatus("已保存 ${record.id} 的配置", false)
                    }
                }
            }
            container.addView(rows.build(record, installed, configure))
        }
    }

    private fun toggle(record: ExternalPluginRecord, checked: Boolean, sw: SwitchCompat) {
        scope.launch {
            val ok = if (checked) enable(record) else disable(record)
            if (ok) {
                refresh()
            } else {
                // 回滚开关状态，避免 UI 与实际不符
                sw.setOnCheckedChangeListener(null)
                sw.isChecked = !checked
                sw.setOnCheckedChangeListener { _, c -> toggle(record, c, sw) }
            }
        }
    }

    private fun enable(record: ExternalPluginRecord): Boolean {
        val loaded = runCatching { app.dexLoader.loadFromFile(record.id) }.getOrNull()
        if (loaded == null) {
            onStatus("启用失败：${record.id} 的 APK 无法加载（可能已被删除或签名变化）", true)
            return false
        }
        if (!app.kernel.installOne(loaded.plugin)) {
            app.dexLoader.unload(record.id, deleteApk = false)
            onStatus("启用失败：${record.id} 与已安装插件冲突", true)
            return false
        }
        app.externalPlugins.setEnabled(record.id, true)
        onStatus("已启用 ${record.id}", false)
        return true
    }

    private fun disable(record: ExternalPluginRecord): Boolean {
        app.kernel.uninstall(record.id)
        app.dexLoader.unload(record.id, deleteApk = false)
        app.externalPlugins.setEnabled(record.id, false)
        onStatus("已禁用 ${record.id}，APK 保留可随时重新启用", false)
        return true
    }

    private fun remove(record: ExternalPluginRecord) {
        app.kernel.uninstall(record.id)
        app.dexLoader.unload(record.id, deleteApk = true)
        app.externalPlugins.remove(record.id)
        onStatus("已移除 ${record.id}", false)
        refresh()
    }
}
