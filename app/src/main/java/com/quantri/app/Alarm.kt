package com.quantri.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Thông tin một lần báo đang kêu. */
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

/** Trạng thái chuông đang kêu, dùng chung giữa dịch vụ và màn hình báo thức (cùng một tiến trình). */
object AlarmState {
    val active = MutableStateFlow<RingInfo?>(null)
}

/**
 * Kêu chuông + rung liên tục như cuộc gọi đến cho tới khi người dùng bấm Tắt
 * (hoặc Báo lại, hoặc hết thời gian tối đa).
 */
class AlarmService : Service() {
    companion object {
        const val ACTION_START = "com.quantri.app.ALARM_START"
        const val ACTION_DISMISS = "com.quantri.app.ALARM_DISMISS"
        const val ACTION_SNOOZE = "com.quantri.app.ALARM_SNOOZE"
        private const val CHANNEL = "ring_alarm"
        private const val NOTIFICATION_ID = 7002
        private const val MAX_RING_MS = 10 * 60_000L
        private const val SNOOZE_MIN = 5

        /** Bắt đầu kêu chuông. Trả về false nếu hệ thống không cho khởi động dịch vụ. */
        fun start(ctx: Context, info: RingInfo): Boolean = runCatching {
            ContextCompat.startForegroundService(
                ctx,
                info.putInto(Intent(ctx, AlarmService::class.java).setAction(ACTION_START))
            )
        }.isSuccess

        fun scheduleSnooze(ctx: Context, info: RingInfo) {
            val am = ctx.getSystemService(AlarmManager::class.java)
            val intent = info.putInto(Intent(ctx, AlarmService::class.java).setAction(ACTION_START))
            val pi = PendingIntent.getForegroundService(
                ctx, 10_000 + info.taskId, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val at = System.currentTimeMillis() + SNOOZE_MIN * 60_000L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }

        fun snoozeMinutes() = SNOOZE_MIN
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var focusRequest: AudioFocusRequest? = null
    private var current: RingInfo? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> stopRinging()
            ACTION_SNOOZE -> {
                current?.let { scheduleSnooze(this, it) }
                stopRinging()
            }
            else -> {
                val info = RingInfo.from(intent) ?: current
                if (info == null) {
                    // Phải gọi startForeground trước khi dừng, nếu không hệ thống sẽ báo lỗi.
                    ensureChannel()
                    enterForeground(
                        NotificationCompat.Builder(this, CHANNEL)
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle("QA LifeOS")
                            .build()
                    )
                    stopRinging()
                } else {
                    startRinging(info)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startRinging(info: RingInfo) {
        val alreadyRinging = current != null
        current = info
        AlarmState.active.value = info
        ensureChannel()
        enterForeground(buildNotification(info))
        if (!alreadyRinging) {
            startSound(info)
            startVibration(info)
        }
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ timeout() }, MAX_RING_MS)
    }

    private fun timeout() {
        current?.let { Reminders.postMissed(this, it) }
        stopRinging()
    }

    private fun stopRinging() {
        releaseResources()
        AlarmState.active.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseResources() {
        handler.removeCallbacksAndMessages(null)
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
        current = null
    }

    override fun onDestroy() {
        releaseResources()
        super.onDestroy()
    }

    // ------------------------------------------------------------ âm thanh và rung

    private fun alarmAttrs(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun startSound(info: RingInfo) {
        if (info.type != ReminderType.ALARM) return

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

    // ------------------------------------------------------------ thông báo toàn màn hình

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val c = NotificationChannel(CHANNEL, "Báo thức nhắc việc", NotificationManager.IMPORTANCE_HIGH)
        c.description = "Màn hình báo thức khi đến giờ làm việc"
        c.setSound(null, null)          // âm thanh do dịch vụ tự phát để kêu lặp lại
        c.enableVibration(false)        // rung do dịch vụ tự điều khiển
        c.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(c)
    }

    private fun buildNotification(info: RingInfo): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val screen = PendingIntent.getActivity(
            this, 3,
            Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags
        )
        val dismiss = PendingIntent.getService(
            this, 4, Intent(this, AlarmService::class.java).setAction(ACTION_DISMISS), flags
        )
        val snooze = PendingIntent.getService(
            this, 5, Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE), flags
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(info.title + "  " + "★".repeat(info.stars))
            .setContentText(info.text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .addAction(0, "Tắt", dismiss)
            .addAction(0, "Báo lại $SNOOZE_MIN phút", snooze)
            .build()
    }

    private fun enterForeground(n: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, n)
            }
        }
    }
}

/** Màn hình báo thức hiện lên trên màn hình khóa, tự bật sáng màn hình. */
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
                    onDismiss = { sendToService(AlarmService.ACTION_DISMISS) },
                    onSnooze = { sendToService(AlarmService.ACTION_SNOOZE) },
                    onGone = { finish() }
                )
            }
        }
    }

    private fun sendToService(action: String) {
        startService(Intent(this, AlarmService::class.java).setAction(action))
    }
}

@Composable
fun AlarmScreen(onDismiss: () -> Unit, onSnooze: () -> Unit, onGone: () -> Unit) {
    val info by AlarmState.active.collectAsState()
    // Khi chuông đã tắt (từ thông báo, hết giờ...) thì đóng màn hình này
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
            Spacer(Modifier.height(8.dp))
            Text("★".repeat(i.stars), color = StarColor, fontSize = 26.sp)
            Spacer(Modifier.height(16.dp))
            Text(i.text, color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) { Text("TẮT", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Báo lại ${AlarmService.snoozeMinutes()} phút", color = Color.White)
            }
        }
    }
}
