package com.quantri.app

import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ReminderType(val label: String) {
    ALARM("Chuông báo"),
    VIBRATE("Rung"),
    NOTIFY("Chỉ hiện thông báo")
}

enum class VibLevel(val label: String) {
    LIGHT("Nhẹ"),
    MEDIUM("Vừa"),
    STRONG("Mạnh")
}

data class Task(
    val id: Int,
    val title: String,
    val stars: Int,                       // 1..5
    val remind: Boolean,
    val type: ReminderType,
    val ringtone: String? = null,         // URI nhạc chuông; null = mặc định
    val vib: VibLevel = VibLevel.MEDIUM
)

/** Khung giờ cố định hằng tuần. day: 1 = Thứ 2 ... 7 = Chủ nhật (giống java.time.DayOfWeek). */
data class Slot(
    val id: Int,
    val taskId: Int,
    val day: Int,
    val startMin: Int,                    // phút tính từ 00:00
    val durationMin: Int
)

/** Việc phát sinh vào một ngày cụ thể. date = LocalDate.toEpochDay(). */
data class Adhoc(
    val id: Int,
    val taskId: Int,
    val date: Long,
    val startMin: Int,
    val durationMin: Int
)

val DAY_LABELS = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
val DAY_NAMES = listOf("Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật")
val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun dayTitle(date: LocalDate): String =
    DAY_NAMES[date.dayOfWeek.value - 1] + ", " + date.format(DATE_FMT)

fun fmtTime(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

fun fmtDuration(min: Int): String {
    val h = min / 60
    val m = min % 60
    return when {
        h == 0 -> "$m phút"
        m == 0 -> "$h giờ"
        else -> "$h giờ $m phút"
    }
}
