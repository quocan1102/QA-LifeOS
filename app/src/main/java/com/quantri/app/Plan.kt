package com.quantri.app

import java.time.LocalDate

enum class Source { TIMETABLE, ADHOC }

/** Một đoạn việc trong kế hoạch ngày: [startMin, endMin) tính bằng phút từ 00:00. */
data class Segment(val taskId: Int, val startMin: Int, val endMin: Int, val source: Source)

object Plan {
    private fun overlaps(a: Segment, b: Segment) = a.startMin < b.endMin && b.startMin < a.endMin

    /** Cắt các đoạn `cuts` ra khỏi `seg`, giữ lại phần còn lại. */
    private fun subtract(seg: Segment, cuts: List<Segment>): List<Segment> {
        var cur = seg.startMin
        val out = mutableListOf<Segment>()
        for (c in cuts.sortedBy { it.startMin }) {
            if (c.endMin <= cur || c.startMin >= seg.endMin) continue
            if (c.startMin > cur) out += seg.copy(endMin = c.startMin, startMin = cur)
            cur = maxOf(cur, c.endMin)
        }
        if (cur < seg.endMin) out += seg.copy(startMin = cur)
        return out
    }

    /**
     * Kế hoạch của một ngày = Thời khóa biểu + Phát sinh, trong đó Phát sinh được ưu tiên:
     *  - Phát sinh CÙNG việc và trùng giờ với một khung cố định: thay thế hẳn khung cố định đó.
     *  - Phát sinh KHÁC việc và trùng giờ: cắt phần bị trùng, phần còn lại của khung cố định được giữ.
     */
    fun forDate(date: LocalDate, slots: List<Slot>, adhoc: List<Adhoc>): List<Segment> {
        val day = date.toEpochDay()
        val extra = adhoc.filter { it.date == day }
            .map { Segment(it.taskId, it.startMin, minOf(it.startMin + it.durationMin, 1440), Source.ADHOC) }
            .filter { it.startMin < it.endMin }

        val fixed = slots.filter { it.day == date.dayOfWeek.value }
            .map { Segment(it.taskId, it.startMin, minOf(it.startMin + it.durationMin, 1440), Source.TIMETABLE) }
            .filter { it.startMin < it.endMin }
            .filterNot { f -> extra.any { it.taskId == f.taskId && overlaps(it, f) } }
            .flatMap { f -> subtract(f, extra.filter { it.taskId != f.taskId && overlaps(it, f) }) }

        return (fixed + extra).sortedWith(compareBy({ it.startMin }, { it.endMin }))
    }
}
