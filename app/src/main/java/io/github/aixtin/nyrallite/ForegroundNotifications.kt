package io.github.aixtin.nyrallite

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/** 前台常驻通知：渠道创建、构建与更新。 */
internal class ForegroundNotifications(private val context: Context) {

    fun build(title: String, body: String): Notification {
        ensureChannel()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    fun update(title: String, body: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.notify(NOTIF_ID, build(title, body)) }
    }

    private fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Nyral Lite 长任务",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "插件长任务运行时的前台通知"
            setShowBadge(false)
        }
        runCatching { nm.createNotificationChannel(channel) }
    }

    companion object {
        const val NOTIF_ID = 10001
        private const val CHANNEL_ID = "nyral_foreground_tasks"
    }
}
