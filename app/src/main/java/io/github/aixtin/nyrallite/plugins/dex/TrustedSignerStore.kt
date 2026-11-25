package io.github.aixtin.nyrallite.plugins.dex

import android.content.Context

/**
 * 用户显式信任的第三方插件签名（APK 证书 SHA-256）。
 * 宿主自身签名不写这里，由 DexPluginLoader.trustSelfSigned() 每次启动时收集。
 */
class TrustedSignerStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): Set<String> = prefs.getStringSet(KEY_SIGNERS, null)?.toSet() ?: emptySet()

    fun add(sha256: String) {
        prefs.edit().putStringSet(KEY_SIGNERS, all() + sha256).apply()
    }

    private companion object {
        private const val PREFS_NAME = "trusted_signers"
        private const val KEY_SIGNERS = "sha256"
    }
}
