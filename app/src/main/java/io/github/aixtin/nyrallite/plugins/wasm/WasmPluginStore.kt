package io.github.aixtin.nyrallite.plugins.wasm

import android.content.Context
import org.json.JSONObject

/**
 * 用户显式信任的 WASM 插件文件哈希，按插件 id 绑定：Map<pluginId, fileSha256>。
 *
 * 与 TrustedApkStore 同构：绑定 id 是刻意的——更新即覆盖写，旧版本回装因哈希对不上
 * 会重新弹窗，「更新需重新确认」不会被回装路径绕开。卸载即删记录。
 */
class WasmPluginStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(pluginId: String): String? = all()[pluginId]

    fun put(pluginId: String, fileSha256: String) {
        val map = all().toMutableMap()
        map[pluginId] = fileSha256
        write(map)
    }

    fun remove(pluginId: String) {
        val map = all().toMutableMap()
        if (map.remove(pluginId) != null) {
            write(map)
        }
    }

    private fun write(map: Map<String, String>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_MAP, obj.toString()).apply()
    }

    private fun all(): Map<String, String> = runCatching {
        val obj = JSONObject(prefs.getString(KEY_MAP, null) ?: "{}")
        obj.keys().asSequence().associateWith { obj.getString(it) }
    }.getOrDefault(emptyMap())

    private companion object {
        private const val PREFS_NAME = "trusted_wasm_plugins"
        private const val KEY_MAP = "hash_map"
    }
}
