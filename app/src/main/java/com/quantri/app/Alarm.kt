package com.quantri.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Thông tin một lần báo đang chờ xác nhận. */
data class RingInfo(
    val key: Int,
    val taskId: Int,
    val title: String,
    val stars: Int,
    val text: String,
    val type: ReminderType,
    val ringtone: String?,
    val vib: VibLevel
) {
    fun putInto(i: Intent): Intent = i
        .putExtra("key", key)
        .putExtra("taskId", taskId)
        .putExtra("title", title)
        .putExtra("stars", stars)
        .putExtra("text", text)
        .putExtra("type", type.name)
        .putExtra("ringtone", ringtone ?: "")
        .putExtra("vib", vib.name)

    companion object {
        fun from(i: Intent?): RingInfo? {
            if (i == null || !i.hasExtra("title")) return null
            return RingInfo(
                key = i.getIntExtra("key", 0),
                taskId = i.getIntExtra("taskId", 0),
                title = i.getStringExtra("title") ?: "",
                stars = i.getIntExtra("stars", 3),
                text = i.getStringExtra("text") ?: "",
                type = runCatching { ReminderType.valueOf(i.getStringExtra("type") ?: "") }
                    .getOrDefault(ReminderType.ALARM),
                ringtone = (i.getStringExtra("ringtone") ?: "").ifEmpty { null },
                vib = runCatching { VibLevel.valueOf(i.getStringExtra("vib") ?: "") }
                    .getOrDefault(VibLevel.MEDIUM)
            )
        }
    }
}

/** Trạng thái "đang kêu", dùng chung giữa dịch vụ và màn hình báo thức (cùng một tiến trình). */
object AlarmState {
    val active = MutableStateFlow<RingInfo?>(null)
}

/**
 * Nhắc việc đến giờ:
 *  - Mỗi vòng kêu/rung tối đa 3 phút (RING_MS), hiện thông báo trên màn hình khóa, thanh trạng thái
 *    và cửa sổ nổi phía trên mọi ứng dụng.
 *  - Nếu chưa xác nhận "Đã làm", nghỉ 3 phút (GAP_MS) rồi nhắc lại, lặp lại cho tới khi xác nhận.
 *  - Vòng kế tiếp được hẹn bằng AlarmManager nên vẫn chạy đúng giờ kể cả khi máy ngủ sâu.
 */
class AlarmService : Service() {
    companion object {
        const val ACTION_START = "com.quantri.app.ALARM_START"
        const val ACTION_MUTE = "com.quantri.app.ALARM_MUTE"
        const val ACTION_DONE = "com.quantri.app.ALARM_DONE"
        private const val CHANNEL = "ring_alarm"
        private const val CHANNEL_WAIT = "ring_wait"
        private const val FOREGROUND_ID = 7002
        private const val WAIT_ID = 7004
        private const val ROUND_REQUEST = 9001
        const val RING_MS = 3 * 60_000L
        const val GAP_MS = 3 * 60_000L

        /** Bắt đầu (hoặc thay thế) một lần nhắc. Trả về false nếu hệ thống không cho khởi động dịch vụ. */
        fun start(ctx: Context, info: RingInfo): Boolean = runCatching {
            ContextCompat.startForegroundService(
                ctx,
                info.putInto(Intent(ctx, AlarmService::class.java).setAction(ACTION_START))
            )
        }.isSuccess

        /** Người dùng xác nhận đã làm: dừng kêu, hủy các vòng nhắc lại và xóa thông báo. */
        fun confirm(ctx: Context) {
            cancelRound(ctx)
            NotificationManagerCompat.from(ctx).cancel(WAIT_ID)
            ctx.stopService(Intent(ctx, AlarmService::class.java))
            AlarmState.active.value = null
        }

        private fun roundIntent(ctx: Context, info: RingInfo?, flags: Int): PendingIntent? {
            val intent = Intent(ctx, AlarmService::class.java).setAction(ACTION_START)
            if (info != null) info.putInto(intent)
            return PendingIntent.getForegroundService(ctx, ROUND_REQUEST, intent, flags)
        }

        private fun cancelRound(ctx: Context) {
            val pi = roundIntent(ctx, null, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
                ?: return
            ctx.getSystemService(AlarmManager::class.java).cancel(pi)
            pi.cancel()
        }

        /** Hẹn vòng kêu kế tiếp sau `delayMs`. */
        private fun scheduleRound(ctx: Context, info: RingInfo, delayMs: Long) {
            val am = ctx.getSystemService(AlarmManager::class.java)
            val pi = roundIntent(
                ctx, info, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            ) ?: return
            val at = System.currentTimeMillis() + delayMs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var focusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var current: RingInfo? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MUTE -> if (current != null) endRound() else stopSelf()
            else -> {
                val info = RingInfo.from(intent) ?: current
                if (info == null) {
                    // Phải gọi startForeground trước khi dừng, nếu không hệ thống sẽ báo lỗi.
                    ensureChannels()
                    enterForeground(
                        NotificationCompat.Builder(this, CHANNEL)
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle("QA LifeOS")
                            .build()
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    startRound(info)
                }
            }
        }
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------ vòng kêu / vòng nghỉ

    private fun startRound(info: RingInfo) {
        releaseSound()
        handler.removeCallbacksAndMessages(null)
        current = info
        AlarmState.active.value = info
        NotificationManagerCompat.from(this).cancel(WAIT_ID)

        ensureChannels()
        enterForeground(buildRingingNotification(info))
        acquireWakeLock()
        if (info.type == ReminderType.ALARM) startSound(info)
        if (info.type != ReminderType.NOTIFY) startVibration(info)

        handler.postDelayed({ endRound() }, RING_MS)
        // Hẹn sẵn vòng sau: nếu dịch vụ bị hệ thống dọn thì báo thức vẫn đánh thức lại.
        scheduleRound(this, info, RING_MS + GAP_MS)
    }

    /** Hết 3 phút (hoặc bấm "Tắt tiếng"): nghỉ 3 phút, thông báo vẫn nằm đó chờ xác nhận. */
    private fun endRound() {
        val info = current ?: run {
            stopSelf()
            return
        }
        handler.removeCallbacksAndMessages(null)
        releaseSound()
        AlarmState.active.value = null
        scheduleRound(this, info, GAP_MS)
        NotificationManagerCompat.from(this).notify(WAIT_ID, buildWaitNotification(info))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releaseSound()
        AlarmState.active.value = null
        current = null
        super.onDestroy()
    }

    private fun releaseSound() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun acquireWakeLock() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "qalifeos:ring")
            .apply { acquire(RING_MS + 10_000L) }
    }

    // ------------------------------------------------------------ âm thanh và rung

    private fun alarmAttrs(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun startSound(info: RingInfo) {
        val am = getSystemService(AudioManager::class.java)
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(alarmAttrs())
            .build()
            .also { am.requestAudioFocus(it) }

        val candidates = listOfNotNull(
            info.ringtone?.let { Uri.parse(it) },
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        )
        for (uri in candidates) {
            val p = MediaPlayer()
            val ok = runCatching {
                p.setAudioAttributes(alarmAttrs())
                p.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
                p.setDataSource(this, uri)
                p.isLooping = true
                p.prepare()
                p.start()
            }.isSuccess
            if (ok) {
                player = p
                return
            }
            p.release()
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration(info: RingInfo) {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }
        vibrator = v
        // Nhịp rung lặp lại liên tục, giống chuông điện thoại: rung - nghỉ - rung - nghỉ...
        val pattern = when (info.vib) {
            VibLevel.LIGHT -> longArrayOf(0, 300, 1500)
            VibLevel.MEDIUM -> longArrayOf(0, 700, 700)
            VibLevel.STRONG -> longArrayOf(0, 1200, 300)
        }
        runCatching { v.vibrate(VibrationEffect.createWaveform(pattern, 0), alarmAttrs()) }
    }

    // ------------------------------------------------------------ thông báo

    private fun ensureChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val c = NotificationChannel(CHANNEL, "Nhắc việc đến giờ", NotificationManager.IMPORTANCE_HIGH)
            c.description = "Thông báo và cửa sổ nhắc khi đến giờ làm việc"
            c.setSound(null, null)          // âm thanh do dịch vụ tự phát để kêu lặp lại
            c.enableVibration(false)        // rung do dịch vụ tự điều khiển
            c.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(c)
        }
        if (nm.getNotificationChannel(CHANNEL_WAIT) == null) {
            val c = NotificationChannel(CHANNEL_WAIT, "Chờ xác nhận", NotificationManager.IMPORTANCE_LOW)
            c.description = "Nhắc việc chưa xác nhận, sẽ nhắc lại sau 3 phút"
            c.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(c)
        }
    }

    private fun doneIntent(): PendingIntent = PendingIntent.getBroadcast(
        this, 6,
        Intent(this, AlarmActionReceiver::class.java).setAction(ACTION_DONE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildRingingNotification(info: RingInfo): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val screen = PendingIntent.getActivity(
            this, 3,
            Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags
        )
        val mute = PendingIntent.getService(
            this, 5, Intent(this, AlarmService::class.java).setAction(ACTION_MUTE), flags
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titleWithStars(info.title, info.stars))
            .setContentText(info.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.text))
            .setCategory(
                if (info.type == ReminderType.NOTIFY) NotificationCompat.CATEGORY_REMINDER
                else NotificationCompat.CATEGORY_ALARM
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setContentIntent(screen)
            .addAction(0, "Đã làm", doneIntent())
        if (info.type != ReminderType.NOTIFY) {
            // Màn hình báo thức đầy đủ trên màn hình khóa; khi đang dùng máy sẽ hiện dạng cửa sổ nổi.
            b.setFullScreenIntent(screen, true)
            b.addAction(0, "Tắt tiếng", mute)
        }
        return b.build()
    }

    private fun buildWaitNotification(info: RingInfo): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_WAIT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titleWithStars(info.title, info.stars))
            .setContentText("Chưa xác nhận · sẽ nhắc lại sau 3 phút")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Đã làm", doneIntent())
            .build()
    }

    private fun enterForeground(n: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(FOREGROUND_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(FOREGROUND_ID, n)
            }
        }
    }
}

/** Nhận nút "Đã làm" bấm từ thông báo. */
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmService.confirm(context)
    }
}

/** Màn hình báo thức hiện trên màn hình khóa, tự bật sáng màn hình. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme {
                AlarmScreen(
                    onDone = { AlarmService.confirm(this) },
                    onMute = {
                        startService(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_MUTE))
                    },
                    onGone = { finish() }
                )
            }
        }
    }
}

@Composable
fun AlarmScreen(onDone: () -> Unit, onMute: () -> Unit, onGone: () -> Unit) {
    val info by AlarmState.active.collectAsState()
    // Khi chuông đã tắt (xác nhận, tắt tiếng, nghỉ giữa các vòng...) thì đóng màn hình này
    LaunchedEffect(info) { if (info == null) onGone() }
    val i = info ?: return

    Column(
        Modifier.fillMaxSize().background(Color(0xFF0B2A6F)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 64.dp)
        ) {
            Text("QA LifeOS · Nhắc việc", color = Color(0xFF9EC5FF), fontSize = 16.sp)
            Spacer(Modifier.height(24.dp))
            Text(
                i.title,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (i.stars > 0) {
                Spacer(Modifier.height(8.dp))
                Text("★".repeat(i.stars), color = StarColor, fontSize = 26.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text(i.text, color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) { Text("ĐÃ LÀM · XÁC NHẬN", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = onMute,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Tắt tiếng (nhắc lại sau 3 phút)", color = Color.White) }
        }
    }
}
