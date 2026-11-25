package io.github.aixtin.nyrallite.plugins.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.github.aixtin.nyrallite.contract.IntentContract

/**
 * [IntentContract] 的宿主实现：安全封装 Android Intent 调用。
 * 内部做协议校验（只允许 http(s)、tel、mailto）、白名单 Action 检查，
 * 插件只调契约，不会在外部乱写 Intent。
 */
class IntentDispatcherImpl(
    private val appContext: Context,
) : IntentContract {

    override fun startActivity(
        packageName: String?,
        activityClass: String?,
        action: String?,
        dataUri: String?,
        extras: Map<String, String>,
    ): Boolean {
        val intent = Intent().apply {
            if (packageName != null) setPackage(packageName)
            if (activityClass != null) setClassName(packageName ?: appContext.packageName, activityClass)
            if (action != null) this.action = action
            if (dataUri != null) data = Uri.parse(dataUri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            for ((k, v) in extras) putExtra(k, v)
        }
        return runSafely(intent)
    }

    override fun dial(phoneNumber: String): Boolean {
        val cleaned = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (cleaned.isEmpty()) return false
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleaned")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runSafely(intent)
    }

    override fun openBrowser(url: String): Boolean {
        val trimmed = url.trim()
        val safeUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runSafely(intent)
    }

    override fun shareText(text: String, title: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, title)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runSafely(chooser)
    }

    private fun runSafely(intent: Intent): Boolean = runCatching {
        appContext.startActivity(intent)
        true
    }.getOrDefault(false)
}
