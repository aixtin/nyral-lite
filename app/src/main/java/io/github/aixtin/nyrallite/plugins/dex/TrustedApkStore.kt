package io.github.aixtin.nyrallite.plugins.dex

import android.content.Context

/**
 * 用户显式信任的插件 APK 文件哈希（整文件 SHA-256）。
 * 信任对象是文件本身而不是签名者证书：更新即新文件，需要重新确认。
 */
class TrustedApkStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): Set<String> = prefs.getStringSet(KEY_HASHES, null)?.toSet() ?: emptySet()

    fun add(fileSha256: String) {
        prefs.edit().putStringSet(KEY_HASHES, all() + fileSha256).apply()
    }

    private companion object {
        private const val PREFS_NAME = "trusted_apks"
        private const val KEY_HASHES = "file_sha256"
    }
}
