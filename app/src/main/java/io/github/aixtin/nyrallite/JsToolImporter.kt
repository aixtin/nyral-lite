package io.github.aixtin.nyrallite

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.aixtin.nyrallite.plugins.js.JsToolMeta
import io.github.aixtin.nyrallite.plugins.js.JsToolMetaResult
import io.github.aixtin.nyrallite.plugins.js.JsToolRepo
import io.github.aixtin.nyrallite.plugins.js.parseJsToolMeta
import io.github.aixtin.nyrallite.plugins.wasm.WasmPluginRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * JS 工具导入：选择 .js 文件 → 解析脚本头部 nyral.tool 元数据 → 信任确认 → 保存。
 * 与 PluginWasmImporter 同一心智：确认前不执行脚本代码，信任对象是文件哈希，
 * 更新（哈希变化）需重新确认；同一文件重装静默通过。
 */
internal class JsToolImporter(
    private val activity: AppCompatActivity,
    private val scope: CoroutineScope,
    private val onStatus: (String, Boolean) -> Unit,
) {
    private val app = activity.application as App

    /** SAF：让用户选 .js 文件导入。 */
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
            import(bytes, displayName(uri) ?: "tool")
        }
    }

    fun launch() {
        launcher.launch(arrayOf("text/javascript", "application/javascript", "text/plain", "*/*"))
    }

    private fun import(bytes: ByteArray, suggestedName: String) {
        val script = bytes.toString(Charsets.UTF_8)
        val meta = when (val r = parseJsToolMeta(script)) {
            is JsToolMetaResult.Ok -> r.meta
            is JsToolMetaResult.Fail -> {
                onStatus("导入失败：${r.reason}", true)
                return
            }
        }
        val toolId = suggestedName.substringBeforeLast(".").ifBlank { "tool" }
        val sha = WasmPluginRuntime.sha256(bytes)

        // 同一文件重装（哈希一致）静默通过；更新（哈希变化）走信任确认
        val installed = JsToolRepo.load(app, toolId)
        if (installed != null &&
            WasmPluginRuntime.sha256(installed.toByteArray(Charsets.UTF_8)) == sha
        ) {
            JsToolRepo.install(app, toolId, script)
            onStatus("已安装：$toolId（此前已信任）", false)
            return
        }
        confirmTrust(toolId, meta, sha, script)
    }

    /** 未信任的工具：展示描述、能力声明、契约版本与文件哈希，用户确认后才保存。 */
    private fun confirmTrust(toolId: String, meta: JsToolMeta, sha: String, script: String) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("未信任的 JS 工具")
            .setMessage(
                buildString {
                    appendLine("工具：$toolId")
                    appendLine("  ${meta.description}")
                    appendLine("能力声明：")
                    if (meta.capabilities.isEmpty()) {
                        appendLine("  （无，纯计算工具）")
                    } else {
                        meta.capabilities.forEach { appendLine("  $it") }
                    }
                    appendLine("契约版本：${meta.contractVersion}")
                    appendLine("文件哈希：")
                    appendLine(formatFingerprint(sha))
                    appendLine()
                    append("未声明的能力在工具层面不存在；已声明的能力经网关按策略放行并审计。工具更新后需重新确认。")
                },
            )
            .setPositiveButton("信任并安装") { _, _ ->
                if (JsToolRepo.install(app, toolId, script)) {
                    onStatus("已安装：$toolId", false)
                } else {
                    onStatus("安装失败：工具文件保存异常", true)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun displayName(uri: Uri): String? =
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }

    /** 把 64 位十六进制指纹按 4 位分组，便于人工核对。 */
    private fun formatFingerprint(sha: String): String =
        sha.uppercase().chunked(4).joinToString(" ")
}
