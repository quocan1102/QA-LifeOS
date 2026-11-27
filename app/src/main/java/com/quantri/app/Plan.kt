package com.quantri.app

import java.time.LocalDate

enum class Source { TIMETABLE, ADHOC }

/**
 * Một đoạn việc trong kế hoạch của MỘT ngày: [startMin, endMin) tính bằng phút từ 00:00 của ngày đó.
 * Mang theo tên và cài đặt nhắc nhở, nên việc phát sinh không cần có trong danh sách việc.
 * originStart/originEnd: thời điểm bắt đầu/kết thúc gốc ("phút cục bộ" = epochDay * 1440 + phút);
 * khác với startMin/endMin khi việc phát sinh kéo dài qua nhiều ngày.
 */
data class Segment(
    val taskId: Int,                 // 0 nếu là việc phát sinh
    val title: String,
    val stars: Int,                  // 0 = không có sao (việc phát sinh)
    val remind: Boolean,
    val type: ReminderType,
    val ringtone: String?,
    val vib: VibLevel,
    val startMin: Int,
    val endMin: Int,
    val source: Source,
    val originStart: Long,
    val originEnd: Long,
    val dayAbs: Long                 // epochDay * 1440 của ngày đang xét
) {
    /** Đoạn này bắt đầu ngay trong ngày (không phải phần nối tiếp từ hôm trước). */
    val startsHere: Boolean get() = originStart >= dayAbs
    val continuesToNext: Boolean get() = originEnd > dayAbs + 1440
    val key: String get() = title.trim().lowercase()
}

object Plan {
    private fun overlaps(a: Segment, b: Segment) = a.startMin < b.endMin && b.startMin < a.endMin

    /** Cắt các đoạn `cuts` ra khỏi `seg`, giữ lại phần còn lại. */
    private fun subtract(seg: Segment, cuts: List<Segment>): List<Segment> {
        var cur = seg.startMin
        val out = mutableListOf<Segment>()
        fun piece(from: Int, to: Int) = seg.copy(
            startMin = from, endMin = to,
            originStart = seg.dayAbs + from, originEnd = seg.dayAbs + to
        )
        for (c in cuts.sortedBy { it.startMin }) {
            if (c.endMin <= cur || c.startMin >= seg.endMin) continue
            if (c.startMin > cur) out += piece(cur, c.startMin)
            cur = maxOf(cur, c.endMin)
        }
        if (cur < seg.endMin) out += piece(cur, seg.endMin)
        return out
    }

    /**
     * Kế hoạch của một ngày = Thời khóa biểu + Phát sinh, trong đó Phát sinh được ưu tiên.
     * "Cùng việc" được so theo TÊN (không phân biệt hoa thường):
     *  - Phát sinh cùng tên và trùng giờ với một khung cố định: thay thế hẳn khung cố định đó.
     *  - Phát sinh khác tên và trùng giờ: cắt phần bị trùng, phần còn lại của khung cố định được giữ.
     * Việc phát sinh kéo dài nhiều ngày xuất hiện ở mọi ngày nó đi qua (được cắt theo từng ngày).
     */
    fun forDate(date: LocalDate, tasks: List<Task>, slots: List<Slot>, adhoc: List<Adhoc>): List<Segment> {
        val dayAbs = date.toEpochDay() * 1440L
        val byId = tasks.associateBy { it.id }

        val extra = adhoc.mapNotNull { a ->
            val s = maxOf(a.absStart, dayAbs)
            val e = minOf(a.absEnd, dayAbs + 1440L)
            if (s >= e) null else Segment(
                0, a.title, 0, a.remind, a.type, a.ringtone, a.vib,
                (s - dayAbs).toInt(), (e - dayAbs).toInt(), Source.ADHOC,
                a.absStart, a.absEnd, dayAbs
            )
        }

        val fixed = slots.filter { it.day == date.dayOfWeek.value }
            .mapNotNull { sl ->
                val t = byId[sl.taskId] ?: return@mapNotNull null
                val end = minOf(sl.startMin + sl.durationMin, 1440)
                if (sl.startMin >= end) null else Segment(
                    t.id, t.title, t.stars, t.remind, t.type, t.ringtone, t.vib,
                    sl.startMin, end, Source.TIMETABLE,
                    dayAbs + sl.startMin, dayAbs + end, dayAbs
                )
            }
            .filterNot { f -> extra.any { it.key == f.key && overlaps(it, f) } }
            .flatMap { f -> subtract(f, extra.filter { it.key != f.key && overlaps(it, f) }) }

        return (fixed + extra).sortedWith(compareBy({ it.startMin }, { it.endMin }))
    }
}
