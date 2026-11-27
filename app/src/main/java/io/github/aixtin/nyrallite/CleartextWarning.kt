package io.github.aixtin.nyrallite

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * http:// 端点明文警告：密钥与对话内容不加密过网，保存前让用户知情。
 * 非 http 地址直接放行；局域网自建服务常走 http，所以只警告不禁止。
 */
internal fun AppCompatActivity.confirmCleartext(url: String, onConfirm: () -> Unit) {
    if (!url.trim().trimEnd('/').startsWith("http://")) {
        onConfirm()
        return
    }
    AlertDialog.Builder(this)
        .setTitle(R.string.cleartext_warning_title)
        .setMessage(R.string.cleartext_warning_message)
        .setNegativeButton(R.string.cleartext_warning_cancel, null)
        .setPositiveButton(R.string.cleartext_warning_save) { _, _ -> onConfirm() }
        .show()
}
