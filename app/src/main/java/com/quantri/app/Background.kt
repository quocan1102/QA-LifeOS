package com.quantri.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate

object KeepAlive {
    private const val CHANNEL = "svc_keepalive"
    const val NOTIFICATION_ID = 7001

    /** Bật hoặc tắt dịch vụ chạy nền theo cài đặt; nếu đang chạy thì cập nhật nội dung thông báo. */
    fun apply(ctx: Context) {
        val intent = Intent(ctx, KeepAliveService::class.java)
        if (Repository(ctx).keepAlive()) {
            runCatching { ContextCompat.startForegroundService(ctx, intent) }
        } else {
            ctx.stopService(intent)
        }
    }

    fun notification(ctx: Context): Notification {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val channel = NotificationChannel(CHANNEL, "Chạy nền", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Giữ QA LifeOS hoạt động để nhắc việc đúng giờ"
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
        }

        val next = Reminders.nextEvent(Repository(ctx).load(), System.currentTimeMillis(), EventKind.START)
        val text = if (next == null) {
            "Chưa có việc nào cần nhắc"
        } else {
            val today = LocalDate.now()
            val whenText = when (next.date) {
                today -> "hôm nay"
                today.plusDays(1) -> "ngày mai"
                else -> next.date.format(DATE_FMT)
            }
            "Kế tiếp: " + next.task.title + " · " + fmtTime(next.seg.startMin) + " " + whenText
        }

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("QA LifeOS đang chạy nền")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}

/** Dịch vụ chạy nền (foreground service) giúp hệ thống ít khi dọn mất app, để nhắc việc luôn đúng giờ. */
class KeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = KeepAlive.notification(this)
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    KeepAlive.NOTIFICATION_ID, n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(KeepAlive.NOTIFICATION_ID, n)
            }
        }.isSuccess
        if (!started) stopSelf()
        return START_STICKY
    }
}
