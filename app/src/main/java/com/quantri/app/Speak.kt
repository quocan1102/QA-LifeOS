package com.quantri.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Thông báo cao cấp: hiện thông báo và đọc thành tiếng nội dung bằng giọng đọc (Text-to-Speech) của máy.
 */
class SpeakService : Service(), TextToSpeech.OnInitListener {
    companion object {
        private const val CHANNEL = "adv_progress"
        private const val NOTIFICATION_ID = 7003

        fun start(ctx: Context, title: String, text: String): Boolean = runCatching {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, SpeakService::class.java)
                    .putExtra("title", title)
                    .putExtra("text", text)
            )
        }.isSuccess

        /** Nút "Nghe thử": dùng tên việc hiện tại và việc kế tiếp THẬT lấy từ kế hoạch. */
        fun test(ctx: Context, a: AdvSettings, current: String, next: String?): Boolean =
            start(ctx, "Sắp hết giờ · $current", Advanced.message(current, a, next))
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra("title") ?: "QA LifeOS"
        val text = intent?.getStringExtra("text") ?: ""
        ensureChannel()
        enterForeground(buildNotification(title, text))

        pendingText = text
        if (tts == null) {
            tts = TextToSpeech(applicationContext, this)
        } else if (ready) {
            speakNow()
        }
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ finish() }, 60_000L)      // an toàn: không giữ dịch vụ quá lâu
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            finish()
            return
        }
        val result = engine.setLanguage(Locale("vi", "VN"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            handler.post {
                Toast.makeText(
                    this,
                    "Máy chưa có giọng đọc tiếng Việt. Hãy cài trong Cài đặt > Văn bản thành giọng nói.",
                    Toast.LENGTH_LONG
                ).show()
            }
            engine.setLanguage(Locale.getDefault())
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                handler.post { finish() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post { finish() }
            }
        })
        ready = true
        speakNow()
    }

    private fun speakNow() {
        val text = pendingText ?: return
        tts?.speak(text.replace("%", " phần trăm"), TextToSpeech.QUEUE_FLUSH, null, "adv")
    }

    private fun finish() {
        handler.removeCallbacksAndMessages(null)
        // Giữ lại thông báo trong khay sau khi đọc xong
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val c = NotificationChannel(CHANNEL, "Sắp hết giờ (thông báo cao cấp)", NotificationManager.IMPORTANCE_HIGH)
        c.description = "Nhắc khi việc đang làm sắp kết thúc, có đọc thành tiếng"
        c.setSound(null, null)              // giọng đọc do dịch vụ phát
        c.enableVibration(true)
        c.vibrationPattern = longArrayOf(0, 250, 150, 250)
        c.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(c)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
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
