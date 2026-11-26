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
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val ALARM_REQUEST = 1

/** Một lần nhắc: đoạn việc `seg` của `task` bắt đầu vào ngày `date`. */
data class Event(val date: LocalDate, val seg: Segment, val task: Task) {
    val at: Long
        get() = date.atTime(seg.startMin / 60, seg.startMin % 60)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

fun ringtoneTitle(ctx: Context, uriStr: String?): String {
    if (uriStr == null) return "Mặc định (chuông báo thức)"
    return runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(uriStr)).getTitle(ctx) }
        .getOrDefault("Nhạc chuông đã chọn")
}

object Reminders {

    private val patterns = mapOf(
        VibLevel.LIGHT to longArrayOf(0, 250),
        VibLevel.MEDIUM to longArrayOf(0, 500, 250, 500),
        VibLevel.STRONG to longArrayOf(0, 900, 250, 900, 250, 900, 250, 900)
    )

    // ------------------------------------------------------------ tính các lần nhắc từ KẾ HOẠCH

    private fun eventsOn(date: LocalDate, d: AppData): List<Event> {
        val byId = d.tasks.associateBy { it.id }
        return Plan.forDate(date, d.slots, d.adhoc).mapNotNull { seg ->
            val t = byId[seg.taskId]
            if (t != null && t.remind) Event(date, seg, t) else null
        }
    }

    /** Lần nhắc kế tiếp (sau `after`), tìm trong vòng ~400 ngày. */
    fun nextEvent(d: AppData, after: Long): Event? {
        val start = Instant.ofEpochMilli(after).atZone(ZoneId.systemDefault()).toLocalDate()
        for (i in 0..400) {
            val e = eventsOn(start.plusDays(i.toLong()), d)
                .filter { it.at > after }
                .minByOrNull { it.at }
            if (e != null) return e
        }
        return null
    }

    fun nextEventAt(d: AppData, after: Long): Long? = nextEvent(d, after)?.at

    /** Các lần nhắc có thời điểm trong khoảng (from, to]. */
    fun eventsBetween(d: AppData, from: Long, to: Long): List<Event> {
        val zone = ZoneId.systemDefault()
        var date = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val last = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        val out = mutableListOf<Event>()
        while (!date.isAfter(last)) {
            out += eventsOn(date, d).filter { it.at > from && it.at <= to }
            date = date.plusDays(1)
        }
        return out.sortedBy { it.at }
    }

    // ------------------------------------------------------------ đặt báo thức (chỉ 1 báo thức, nối tiếp nhau)

    private fun pendingIntent(ctx: Context, create: Boolean): PendingIntent? {
        val mode = if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(
            ctx, ALARM_REQUEST, Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or mode
        )
    }

    fun reschedule(ctx: Context, d: AppData, after: Long = System.currentTimeMillis()) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        pendingIntent(ctx, false)?.let {
            am.cancel(it)
            it.cancel()
        }
        val at = nextEventAt(d, after) ?: return
        val pi = pendingIntent(ctx, true) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    // ------------------------------------------------------------ kênh thông báo (âm thanh/rung nằm ở kênh)

    private fun channelId(t: Task): String = when (t.type) {
        ReminderType.NOTIFY -> "r_notify"
        ReminderType.VIBRATE -> "r_vibrate_" + t.vib.name
        ReminderType.ALARM ->
            "r_alarm_" + t.vib.name + "_" + Integer.toHexString((t.ringtone ?: "default").hashCode())
    }

    fun ensureChannel(ctx: Context, t: Task): String {
        val id = channelId(t)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(id) != null) return id

        val pattern = patterns.getValue(t.vib)
        val channel = when (t.type) {
            ReminderType.NOTIFY -> NotificationChannel(
                id, "Nhắc việc · chỉ thông báo", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }

            ReminderType.VIBRATE -> NotificationChannel(
                id, "Nhắc việc · rung " + t.vib.label.lowercase(), NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = pattern
            }

            ReminderType.ALARM -> {
                val uri = t.ringtone?.let { Uri.parse(it) }
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                NotificationChannel(
                    id,
                    "Nhắc việc · chuông · " + ringtoneTitle(ctx, t.ringtone) + " · rung " + t.vib.label.lowercase(),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(
                        uri,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    enableVibration(true)
                    vibrationPattern = pattern
                }
            }
        }
        nm.createNotificationChannel(channel)
        return id
    }

    /** Tạo kênh cho các việc đang bật nhắc và xóa các kênh không còn ai dùng. */
    fun syncChannels(ctx: Context, tasks: List<Task>) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val used = tasks.filter { it.remind }.map { ensureChannel(ctx, it) }.toSet()
        nm.notificationChannels
            .filter { it.id.startsWith("r_") && it.id !in used }
            .forEach { nm.deleteNotificationChannel(it.id) }
    }

    // ------------------------------------------------------------ hiển thị thông báo

    fun post(ctx: Context, t: Task, nid: Int, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val channel = ensureChannel(ctx, t)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val loud = t.type != ReminderType.NOTIFY
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(t.title + "  " + "★".repeat(t.stars))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(if (loud) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(
                if (t.type == ReminderType.ALARM) NotificationCompat.CATEGORY_ALARM
                else NotificationCompat.CATEGORY_REMINDER
            )
            .build()
        NotificationManagerCompat.from(ctx).notify(nid, n)
    }

    /**
     * Đến giờ nhắc:
     *  - Chuông báo / Rung: kêu và rung liên tục như cuộc gọi đến cho tới khi bấm Tắt (AlarmService).
     *  - Chỉ thông báo: hiện một thông báo yên lặng.
     */
    fun show(ctx: Context, e: Event) {
        val nid = ((e.date.toEpochDay() * 1440L + e.seg.startMin) * 31L + e.task.id).toInt()
        val text = fmtTime(e.seg.startMin) + " – " + fmtTime(e.seg.endMin) +
            " · " + fmtDuration(e.seg.endMin - e.seg.startMin) +
            if (e.seg.source == Source.ADHOC) " · Phát sinh" else ""
        ring(ctx, e.task, nid, text)
    }

    private fun ring(ctx: Context, t: Task, nid: Int, text: String) {
        if (t.type == ReminderType.NOTIFY) {
            post(ctx, t, nid, text)
            return
        }
        val info = RingInfo(nid, t.id, t.title, t.stars, text, t.type, t.ringtone, t.vib)
        // Nếu hệ thống không cho khởi động dịch vụ kêu chuông thì dùng thông báo có âm thanh làm dự phòng.
        if (!AlarmService.start(ctx, info)) post(ctx, t, nid, text)
    }

    fun postMissed(ctx: Context, info: RingInfo) {
        val t = Task(info.taskId, info.title, info.stars, true, ReminderType.NOTIFY)
        post(ctx, t, info.key, "Bạn đã bỏ lỡ nhắc nhở · " + info.text)
    }

    /** Nút "Thử ngay" trong hộp thoại sửa việc. */
    fun test(ctx: Context, t: Task) = ring(ctx, t, 999_999, "Đây là thông báo thử")
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = Repository(context)
        val data = repo.load()
        val now = System.currentTimeMillis()
        val upTo = now + 1000
        val from = maxOf(repo.lastFired(), now - 15 * 60_000L)
        Reminders.eventsBetween(data, from, upTo).forEach { Reminders.show(context, it) }
        repo.setLastFired(upTo)
        Reminders.reschedule(context, data, after = upTo)
        KeepAlive.apply(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.reschedule(context, Repository(context).load())
        KeepAlive.apply(context)
    }
}
