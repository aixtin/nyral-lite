package io.github.aixtin.nyrallite.plugins.dex

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 外部插件的一条持久化记录（APK 本体在 filesDir/plugins/{id}.apk）。 */
data class ExternalPluginRecord(
    val id: String,
    val version: String,
    val enabled: Boolean,
    val installedAt: Long,
)

/**
 * 外部 Dex 插件的清单持久化。
 * 只存元数据，不存 APK；App 冷启动时按 [all] 里的 enabled 记录恢复加载。
 */
class ExternalPluginStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<ExternalPluginRecord> = decode(prefs.getString(KEY_RECORDS, null))

    fun get(id: String): ExternalPluginRecord? = all().firstOrNull { it.id == id }

    /** 新增或覆盖同 id 记录。 */
    fun upsert(record: ExternalPluginRecord) {
        save(all().filterNot { it.id == record.id } + record)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val current = get(id) ?: return
        upsert(current.copy(enabled = enabled))
    }

    fun remove(id: String) {
        save(all().filterNot { it.id == id })
    }

    private fun save(records: List<ExternalPluginRecord>) {
        val arr = JSONArray()
        for (r in records) {
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("version", r.version)
                    put("enabled", r.enabled)
                    put("installedAt", r.installedAt)
                },
            )
        }
        prefs.edit().putString(KEY_RECORDS, arr.toString()).apply()
    }

    private fun decode(raw: String?): List<ExternalPluginRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ExternalPluginRecord(
                    id = id,
                    version = o.optString("version", ""),
                    enabled = o.optBoolean("enabled", true),
                    installedAt = o.optLong("installedAt", 0L),
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private companion object {
        private const val PREFS_NAME = "external_plugins"
        private const val KEY_RECORDS = "records"
    }
}
