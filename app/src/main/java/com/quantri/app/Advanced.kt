package com.quantri.app

import java.time.Instant
import java.time.ZoneId

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
        if (percent) value.coerceIn(1, 99) else value.coerceIn(1, 100_000)

    /** Thời điểm nhắc, tính bằng mili-giây kể từ lúc việc bắt đầu; null nếu không hợp lệ với việc này. */
    fun offsetMs(durationMin: Long, a: AdvSettings): Long? {
        val total = durationMin * 60_000L
        val amount = if (a.percent) total * a.value / 100 else a.value * 60_000L
        val offset = if (a.remaining) total - amount else amount
        return if (offset in 1 until total) offset else null
    }

    /**
     * Tên việc sẽ làm SAU đoạn việc thứ `index` trong kế hoạch ngày `segs`:
     * là việc khác tên đầu tiên đứng sau nó (bỏ qua các đoạn cùng tên nối liền nhau).
     */
    fun nextTitleAfter(segs: List<Segment>, index: Int): String? {
        val current = segs[index]
        for (j in index + 1 until segs.size) {
            if (segs[j].key != current.key) return segs[j].title
        }
        return null
    }

    /**
     * Việc đang làm (hoặc việc sắp tới nếu lúc này đang trống) theo kế hoạch, kèm việc sẽ làm sau nó.
     * Dùng cho phần xem trước và nút "Nghe thử" để luôn dùng tên việc thật, không dùng tên mẫu.
     * Trả về null nếu kế hoạch (trong 60 ngày tới) chưa có việc nào.
     */
    fun currentAndNext(
        tasks: List<Task>,
        slots: List<Slot>,
        adhoc: List<Adhoc>,
        now: Long
    ): Pair<String, String?>? {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        for (i in 0..60) {
            val date = today.plusDays(i.toLong())
            val segs = Plan.forDate(date, tasks, slots, adhoc)
            val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val idx = segs.indexOfFirst { dayStart + it.endMin * 60_000L > now }
            if (idx >= 0) return segs[idx].title to nextTitleAfter(segs, idx)
        }
        return null
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
