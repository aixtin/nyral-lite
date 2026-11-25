package io.github.aixtin.nyrallite.plugins.storageprefs

import android.content.Context
import io.github.aixtin.nyrallite.kernel.ConfigBackend

/**
 * 内核配置后端：按 "命名空间/键" 扁平存放。
 * 每个插件的配置落在同一个私有 prefs 文件里，互不覆盖。
 */
class SharedPrefsConfigBackend(context: Context) : ConfigBackend {

    private val sp = context.applicationContext
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override fun read(ns: String, key: String): String? = sp.getString(keyOf(ns, key), null)

    override fun write(ns: String, key: String, value: String) {
        sp.edit().putString(keyOf(ns, key), value).apply()
    }

    override fun remove(ns: String, key: String) {
        sp.edit().remove(keyOf(ns, key)).apply()
    }

    private fun keyOf(ns: String, key: String) = "$ns/$key"

    private companion object {
        const val NAME = "nyral_lite_plugin_config"
    }
}
