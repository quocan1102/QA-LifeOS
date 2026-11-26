package com.quantri.app

/**
 * Cài đặt "Thông báo cao cấp": nhắc khi việc đang làm sắp kết thúc.
 *  remaining = false: nhắc khi đã qua `value` (% hoặc phút) thời gian của việc.
 *  remaining = true : nhắc khi còn lại `value` (% hoặc phút).
 */
data class AdvSettings(
    val enabled: Boolean = true,
    val remaining: Boolean = false,
    val value: Int = 90,
    val percent: Boolean = true
)

object Advanced {
    fun clamp(value: Int, percent: Boolean): Int =
        if (percent) value.coerceIn(1, 99) else value.coerceIn(1, 1440)

    /** Thời điểm nhắc, tính bằng mili-giây kể từ lúc việc bắt đầu; null nếu không hợp lệ với việc này. */
    fun offsetMs(durationMin: Int, a: AdvSettings): Long? {
        val total = durationMin * 60_000L
        val amount = if (a.percent) total * a.value / 100 else a.value * 60_000L
        val offset = if (a.remaining) total - amount else amount
        return if (offset in 1 until total) offset else null
    }

    /** Nội dung thông báo (cũng là câu được đọc thành tiếng). */
    fun message(title: String, a: AdvSettings, nextTitle: String?): String {
        val unit = if (a.percent) "%" else " phút"
        val phrase = (if (a.remaining) "còn lại " else "đã trôi qua ") + a.value + unit
        val tail = if (nextTitle != null) {
            "Hãy tập trung hoàn thành để chuẩn bị $nextTitle."
        } else {
            "Hãy tập trung hoàn thành."
        }
        return "Thời gian $title $phrase. $tail"
    }
}
