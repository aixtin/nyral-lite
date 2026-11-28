package io.github.aixtin.nyrallite.plugins.js

import android.content.Context
import org.json.JSONObject

/**
 * 用户显式信任的 JS 工具文件哈希，按工具 id 绑定：Map<toolId, fileSha256>。
 *
 * 与 WasmPluginStore 同构：绑定 id 是刻意的——更新即覆盖写，旧版本回装因哈希对不上
 * 会重新弹窗，「更新需重新确认」不会被回装路径绕开。卸载即删记录。
 * 运行时（JsToolRunner）每次执行前都拿文件哈希与登记值核对，篡改文件即拒绝运行。
 */
class JsToolStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(toolId: String): String? = all()[toolId]

    fun put(toolId: String, fileSha256: String) {
        val map = all().toMutableMap()
        map[toolId] = fileSha256
        write(map)
    }

    fun remove(toolId: String) {
        val map = all().toMutableMap()
        if (map.remove(toolId) != null) {
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
        private const val PREFS_NAME = "trusted_js_tools"
        private const val KEY_MAP = "hash_map"
    }
}
