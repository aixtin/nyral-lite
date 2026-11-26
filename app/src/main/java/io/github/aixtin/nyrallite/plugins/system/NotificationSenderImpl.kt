package io.github.aixtin.nyrallite.plugins.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.aixtin.nyrallite.contract.NotificationContract

/**
 * [NotificationContract] 的宿主实现：调 Android NotificationManager。
 * 插件不直接 import android.app.NotificationManager，只认契约。
 */
class NotificationSenderImpl(
    private val appContext: Context,
) : NotificationContract {

    private val nm: NotificationManager by lazy {
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun send(
        title: String,
        body: String,
        channelId: String,
        tag: String?,
        id: Int,
    ) {
        ensureChannel(channelId)
        // 未授予通知权限时直接放弃，避免 notify 抛 SecurityException
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val safeTitle = title.take(64)
        val safeBody = body.take(400)

        val builder = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(safeTitle)
            .setContentText(safeBody)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setStyle(NotificationCompat.BigTextStyle().bigText(safeBody))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        runCatching {
            NotificationManagerCompat.from(appContext).notify(tag, id, builder.build())
        }
    }

    override fun cancel(tag: String?, id: Int) {
        runCatching { nm.cancel(tag, id) }
    }

    private fun ensureChannel(channelId: String) {
        val channel = NotificationChannel(
            channelId,
            "Nyral Lite 系统通知",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Nyral Lite AI 主动触发的通知"
        }
        runCatching { nm.createNotificationChannel(channel) }
    }
}
