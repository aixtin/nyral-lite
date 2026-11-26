package io.github.aixtin.nyrallite

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新检查：请求 GitHub 最新 Release，与本地 versionName 做语义化比较；
 * 有新版本时弹一次对话框，引导用户前往 Release 页下载。
 *
 * 轻量取舍：只查 GitHub 一个源、只在启动时查一次、同版本 24 小时最多提示一次。
 * 版本比较用数字逐段比较，禁止字符串比较（v0.3.10 会被判小于 v0.3.9）。
 */
internal object UpdateChecker {

    private const val API = "https://api.github.com/repos/aixtin/nyral-lite/releases/latest"
    private const val PAGE = "https://github.com/aixtin/nyral-lite/releases/latest"
    private const val NET_TIMEOUT_MS = 5_000
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private const val PROMPT_INTERVAL_MS = 24 * 60 * 60 * 1000L

    private const val PREFS = "update_prefs"
    private const val KEY_LAST_CHECK = "last_check_at"
    private const val KEY_LAST_PROMPT_AT = "last_prompt_at"
    private const val KEY_LAST_PROMPT_VER = "last_prompt_version"

    /** 启动入口：距上次检查不足 6 小时直接返回，否则后台拉取并（必要时）弹窗。 */
    fun checkIfDue(activity: Activity) {
        val sp = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = sp.getLong(KEY_LAST_CHECK, 0L)
        // 时钟回拨（last > now）时放行，避免被永久静默
        if (last in 1..now && now - last < CHECK_INTERVAL_MS) return
        sp.edit().putLong(KEY_LAST_CHECK, now).apply()

        val app = activity.applicationContext
        Thread {
            val latest = fetchLatestTag() ?: return@Thread
            val current = currentVersion(app)
            if (compareVersion(latest, current) <= 0) return@Thread
            activity.runOnUiThread { maybePrompt(activity, latest) }
        }.apply { isDaemon = true }.start()
    }

    /** 拉取最新 Release 的 tag；失败或为草稿/预发布时返回 null。 */
    private fun fetchLatestTag(): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(API).openConnection() as HttpURLConnection
            conn.connectTimeout = NET_TIMEOUT_MS
            conn.readTimeout = NET_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "NyralLite-Android")
            if (conn.responseCode != 200) return null
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(raw)
            if (o.optBoolean("draft", false) || o.optBoolean("prerelease", false)) return null
            o.optString("tag_name", "").trim().ifBlank { null }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /** 同版本 24 小时内最多弹一次，避免每次回前台都打扰。 */
    private fun maybePrompt(activity: Activity, version: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        val sp = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastAt = sp.getLong(KEY_LAST_PROMPT_AT, 0L)
        val lastVer = sp.getString(KEY_LAST_PROMPT_VER, null)
        val clockOk = lastAt in 1..now
        if (lastVer == version && clockOk && now - lastAt < PROMPT_INTERVAL_MS) return
        sp.edit().putLong(KEY_LAST_PROMPT_AT, now).putString(KEY_LAST_PROMPT_VER, version).apply()

        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.upd_title, version.trimStart('v')))
            .setMessage(R.string.upd_message)
            .setNegativeButton(R.string.upd_later, null)
            .setPositiveButton(R.string.upd_go) { _, _ ->
                runCatching {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PAGE)))
                }
            }
            .show()
    }

    /** 已安装版本名，取不到时给 "0"。 */
    private fun currentVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    /**
     * 语义化版本比较：a>b 返回 1，相等返回 0，a<b 返回 -1。
     * 只取数字段逐位比较，兼容 "v1.2"、"1.2.0" 等形式。
     */
    fun compareVersion(a: String, b: String): Int {
        val pa = a.replace(Regex("[^0-9.]"), "").split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.replace(Regex("[^0-9.]"), "").split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return if (x > y) 1 else -1
        }
        return 0
    }
}
