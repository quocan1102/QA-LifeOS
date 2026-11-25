package com.quantri.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private const val EXTRA_SLOT_ID = "slot_id"

object Reminders {

    /** Thời điểm kế tiếp (sau `afterMillis`) mà khung giờ này bắt đầu. */
    fun nextTrigger(slot: Slot, afterMillis: Long = System.currentTimeMillis()): Long {
        val zone = ZoneId.systemDefault()
        val after = ZonedDateTime.ofInstant(Instant.ofEpochMilli(afterMillis), zone)
        val time = LocalTime.of(slot.startMin / 60, slot.startMin % 60)
        var date = after.toLocalDate()
        repeat(8) {
            if (date.dayOfWeek.value == slot.day) {
                val candidate = ZonedDateTime.of(date, time, zone)
                if (candidate.isAfter(after)) return candidate.toInstant().toEpochMilli()
            }
            date = date.plusDays(1)
        }
        return afterMillis + 7L * 24 * 60 * 60 * 1000
    }

    private fun pendingIntent(ctx: Context, slotId: Int, create: Boolean): PendingIntent? {
        val intent = Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_SLOT_ID, slotId)
        val mode = if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(ctx, slotId, intent, PendingIntent.FLAG_IMMUTABLE or mode)
    }

    fun cancel(ctx: Context, slotId: Int) {
        val pi = pendingIntent(ctx, slotId, create = false) ?: return
        ctx.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }

    fun schedule(
        ctx: Context,
        slot: Slot,
        task: Task?,
        after: Long = System.currentTimeMillis()
    ) {
        cancel(ctx, slot.id)
        if (task == null || !task.remind) return
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(ctx, slot.id, create = true) ?: return
        val at = nextTrigger(slot, after)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun scheduleAll(ctx: Context, tasks: List<Task>, slots: List<Slot>) {
        val byId = tasks.associateBy { it.id }
        slots.forEach { schedule(ctx, it, byId[it.taskId]) }
    }

    private fun channelId(type: ReminderType) = "reminder_" + type.name.lowercase()

    // Âm thanh/rung của thông báo được quy định theo "kênh", nên mỗi kiểu nhắc có một kênh riêng.
    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val pattern = longArrayOf(0, 500, 300, 500, 300, 500)

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val alarm = NotificationChannel(
            channelId(ReminderType.ALARM), "Nhắc việc - Chuông báo",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(
                alarmUri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIC)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = pattern
        }

        val vibrate = NotificationChannel(
            channelId(ReminderType.VIBRATE), "Nhắc việc - Rung",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = pattern
        }

        val notify = NotificationChannel(
            channelId(ReminderType.NOTIFY), "Nhắc việc - Chỉ hiện thông báo",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }

        nm.createNotificationChannel(alarm)
        nm.createNotificationChannel(vibrate)
        nm.createNotificationChannel(notify)
    }

    fun show(ctx: Context, slot: Slot, task: Task) {
        createChannels(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val loud = task.type != ReminderType.NOTIFY
        val n = NotificationCompat.Builder(ctx, channelId(task.type))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title + "  " + "★".repeat(task.stars))
            .setContentText("${fmtTime(slot.startMin)} · ${fmtDuration(slot.durationMin)}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(if (loud) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(
                if (task.type == ReminderType.ALARM) NotificationCompat.CATEGORY_ALARM
                else NotificationCompat.CATEGORY_REMINDER
            )
            .build()
        NotificationManagerCompat.from(ctx).notify(slot.id, n)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slotId = intent.getIntExtra(EXTRA_SLOT_ID, -1)
        val repo = Repository(context)
        val slot = repo.loadSlots().firstOrNull { it.id == slotId } ?: return
        val task = repo.loadTasks().firstOrNull { it.id == slot.taskId } ?: return
        if (task.remind) Reminders.show(context, slot, task)
        // Đặt lại cho tuần sau
        Reminders.schedule(context, slot, task, after = System.currentTimeMillis() + 60_000)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = Repository(context)
        Reminders.scheduleAll(context, repo.loadTasks(), repo.loadSlots())
    }
}
