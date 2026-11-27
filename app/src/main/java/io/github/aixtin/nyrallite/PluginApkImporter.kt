package io.github.aixtin.nyrallite

import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import io.github.aixtin.nyrallite.plugins.dex.ExternalPluginRecord
import io.github.aixtin.nyrallite.plugins.dex.StagedApk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 外部 Dex 插件的 APK 导入：选择文件 → 暂存 → 信任确认 → 装入内核并记录。
 * 未信任的 APK 先弹窗确认（信任对象是文件哈希），确认前不执行插件代码。
 */
internal class PluginApkImporter(
    private val activity: AppCompatActivity,
    private val scope: CoroutineScope,
    private val onStatus: (String, Boolean) -> Unit,
    private val onInstalled: () -> Unit,
) {
    private val app = activity.application as App

    /** SAF：让用户选 APK 文件导入动态插件。 */
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@registerForActivityResult
        scope.launch {
            val staged = runCatching {
                app.contentResolver.openInputStream(uri)?.use { app.dexLoader.stage(it) }
            }.getOrNull()
            if (staged == null) {
                onStatus("导入失败：不是有效的插件 APK", true)
                return@launch
            }
            if (app.dexLoader.isTrusted(staged.fileSha256)) finishImport(staged)
            else confirmThirdPartySigner(staged)
        }
    }

    fun launch() {
        launcher.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
    }

    /** 签名已受信任：加载并装入内核，写入持久化记录。 */
    private fun finishImport(staged: StagedApk) {
        val loaded = app.dexLoader.installStaged(staged)
        if (loaded == null) {
            onStatus("导入失败：APK 未受信任或入口类未找到", true)
            return
        }
        // 同 id 视为升级：先卸旧插件（回收它注册的服务与 effect），再装新版本
        if (activity.kernel.isInstalled(loaded.pluginId)) activity.kernel.uninstall(loaded.pluginId)
        if (!activity.kernel.installOne(loaded.plugin)) {
            app.dexLoader.unload(loaded.pluginId, deleteApk = true)
            onStatus("安装失败：${loaded.pluginId} 的 install() 抛异常，详情看 logcat", true)
            return
        }
        // 首次导入默认启用；已存在同 id 则覆盖记录（视为升级）
        app.externalPlugins.upsert(
            ExternalPluginRecord(
                id = loaded.pluginId,
                version = loaded.plugin.manifest.version,
                enabled = true,
                installedAt = System.currentTimeMillis(),
            ),
        )
        onStatus("已导入并启用：${loaded.pluginId} v${loaded.plugin.manifest.version}", false)
        onInstalled()
    }

    /** 未信任的 APK：展示包信息、证书指纹与文件哈希，用户确认后才信任并加载。 */
    private fun confirmThirdPartySigner(staged: StagedApk) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("未信任的插件")
            .setMessage(
                buildString {
                    appendLine("包名：${staged.packageName ?: "未知"}")
                    appendLine("版本：${staged.versionName ?: "未知"}")
                    appendLine("签名指纹：")
                    appendLine(staged.certFingerprint?.let(::formatFingerprint) ?: "未知")
                    appendLine("文件哈希：")
                    appendLine(formatFingerprint(staged.fileSha256))
                    appendLine()
                    append("信任后该插件的代码将以主应用的身份运行，请确认来源可靠。插件更新后需重新确认。")
                },
            )
            .setPositiveButton("信任并安装") { _, _ ->
                app.dexLoader.trust(staged.fileSha256)
                finishImport(staged)
            }
            .setNegativeButton("取消") { _, _ -> staged.file.delete() }
            .setOnCancelListener { staged.file.delete() }
            .show()
    }

    /** 把 64 位十六进制指纹按 4 位分组，便于人工核对。 */
    private fun formatFingerprint(sha: String): String =
        sha.uppercase().chunked(4).joinToString(" ")
}
