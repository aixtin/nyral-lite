package io.github.aixtin.nyrallite

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import io.github.aixtin.nyrallite.plugins.wasm.WasmPluginGateway
import io.github.aixtin.nyrallite.plugins.wasm.WasmPluginMetaResult
import io.github.aixtin.nyrallite.plugins.wasm.WasmPluginRuntime
import io.github.aixtin.nyrallite.plugins.wasm.WasmPluginStore
import io.github.aixtin.nyrallite.plugins.wasm.WasmToolSource
import io.github.aixtin.nyrallite.plugins.wasm.parseWasmPluginMeta
import io.github.charlietap.chasm.embedding.module
import io.github.charlietap.chasm.embedding.shapes.ChasmResult
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WASM 插件导入：选择 .wasm 文件 → 解析能力声明与工具元数据 → 信任确认 → 登记哈希并保存。
 * 与 PluginApkImporter 同一心智：确认前不执行插件代码，信任对象是文件哈希，更新需重新确认。
 *
 * 工具元数据（自定义段 nyral.tool）在此一并校验：声明了工具但元数据不合法的插件
 * 拒绝安装——模型侧工具清单只收校验通过的声明，不让坏元数据混进模型的工具列表。
 */
internal class PluginWasmImporter(
    private val activity: AppCompatActivity,
    private val scope: CoroutineScope,
    private val onStatus: (String, Boolean) -> Unit,
) {
    private val app = activity.application as App

    /** SAF：让用户选 .wasm 文件导入。 */
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@registerForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null) {
                onStatus("导入失败：读不到文件", true)
                return@launch
            }
            import(bytes, displayName(uri) ?: "plugin")
        }
    }

    fun launch() {
        launcher.launch(arrayOf("application/wasm", "application/octet-stream", "*/*"))
    }

    private fun import(bytes: ByteArray, suggestedName: String) {
        // 解析模块，提取能力声明（导入的 nyral.* 能力函数）与工具元数据
        val declared: List<String>
        val meta: WasmPluginMetaResult
        when (val r = module(bytes)) {
            is ChasmResult.Success -> {
                declared = r.result.imports
                    .filter { it.moduleName == "nyral" && it.entityName in WasmPluginGateway.CAPABILITY_IMPORTS }
                    .map { "${it.moduleName}.${it.entityName}" }
                meta = parseWasmPluginMeta(bytes)
            }
            is ChasmResult.Error -> {
                onStatus("导入失败：不是有效的 WASM 插件（${r.error}）", true)
                return
            }
        }
        if (meta is WasmPluginMetaResult.Fail) {
            onStatus("导入失败：${meta.reason}", true)
            return
        }
        val pluginId = suggestedName.substringBeforeLast(".").ifBlank { "plugin" }
        val sha = WasmPluginRuntime.sha256(bytes)
        val store = WasmPluginStore(app)

        if (store.get(pluginId)?.equals(sha, ignoreCase = true) == true) {
            savePluginFile(pluginId, bytes)
            onStatus("已安装：$pluginId（此前已信任）", false)
            return
        }
        confirmTrust(pluginId, declared, meta, sha, bytes, store)
    }

    /** 未信任的插件：展示能力声明、工具信息与文件哈希，用户确认后才登记并保存。 */
    private fun confirmTrust(
        pluginId: String,
        declared: List<String>,
        meta: WasmPluginMetaResult,
        sha: String,
        bytes: ByteArray,
        store: WasmPluginStore,
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("未信任的 WASM 插件")
            .setMessage(
                buildString {
                    appendLine("插件：$pluginId")
                    if (meta is WasmPluginMetaResult.Ok) {
                        appendLine("工具：${WasmToolSource.toolName(pluginId, meta.meta.toolName)}")
                        appendLine("  ${meta.meta.description}")
                    }
                    appendLine("能力声明：")
                    if (declared.isEmpty()) {
                        appendLine("  （无，纯计算插件）")
                    } else {
                        declared.forEach { appendLine("  $it") }
                    }
                    appendLine("文件哈希：")
                    appendLine(formatFingerprint(sha))
                    appendLine()
                    append("未声明的能力在插件层面不存在；已声明的能力经网关按策略放行并审计。插件更新后需重新确认。")
                },
            )
            .setPositiveButton("信任并安装") { _, _ ->
                store.put(pluginId, sha)
                savePluginFile(pluginId, bytes)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun savePluginFile(pluginId: String, bytes: ByteArray) {
        runCatching {
            val dir = File(app.filesDir, WASM_PLUGIN_DIR).apply { mkdirs() }
            File(dir, "$pluginId.wasm").writeBytes(bytes)
        }.onSuccess {
            onStatus("已安装：$pluginId", false)
        }.onFailure {
            onStatus("安装失败：插件文件保存异常", true)
        }
    }

    private fun displayName(uri: Uri): String? =
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    /** 把 64 位十六进制指纹按 4 位分组，便于人工核对。 */
    private fun formatFingerprint(sha: String): String =
        sha.uppercase().chunked(4).joinToString(" ")

    private companion object {
        const val WASM_PLUGIN_DIR = "wasm_plugins"
    }
}
