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

/** Nhóm công việc do người dùng tự đặt tên; có thể thu gọn / mở rộng. */
data class TaskGroup(
    val id: Int,
    val name: String,
    val collapsed: Boolean = false
)

data class Task(
    val id: Int,
    val title: String,
    val stars: Int,                       // 1..5
    val remind: Boolean,
    val type: ReminderType,
    val ringtone: String? = null,         // URI nhạc chuông; null = mặc định
    val vib: VibLevel = VibLevel.MEDIUM,
    val groupId: Int? = null              // null = chưa phân nhóm
)

/** Khung giờ cố định hằng tuần. day: 1 = Thứ 2 ... 7 = Chủ nhật (giống java.time.DayOfWeek). */
data class Slot(
    val id: Int,
    val taskId: Int,
    val day: Int,
    val startMin: Int,                    // phút tính từ 00:00
    val durationMin: Int
)

/**
 * Việc phát sinh: tự đặt tên, có thể kéo dài nhiều ngày.
 * Ngày lưu dạng LocalDate.toEpochDay(); giờ là số phút trong ngày (0..1439).
 */
data class Adhoc(
    val id: Int,
    val title: String,
    val startDate: Long,
    val startMin: Int,
    val endDate: Long,
    val endMin: Int,
    val remind: Boolean,
    val type: ReminderType,
    val ringtone: String? = null,
    val vib: VibLevel = VibLevel.MEDIUM
) {
    /** Thời điểm tuyệt đối tính bằng "phút cục bộ" = epochDay * 1440 + phút trong ngày. */
    val absStart: Long get() = startDate * 1440L + startMin
    val absEnd: Long get() = endDate * 1440L + endMin

    /** Việc này có đi qua ngày `epoch` (LocalDate.toEpochDay()) không. */
    fun coversDate(epoch: Long): Boolean = absStart < (epoch + 1) * 1440L && absEnd > epoch * 1440L
}

val DAY_LABELS = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
val DAY_NAMES = listOf("Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật")
val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
val DAY_MONTH_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

fun dayTitle(date: LocalDate): String =
    DAY_NAMES[date.dayOfWeek.value - 1] + ", " + date.format(DATE_FMT)

fun fmtTime(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

fun fmtDuration(min: Long): String {
    val d = min / 1440
    val h = (min % 1440) / 60
    val m = min % 60
    val parts = mutableListOf<String>()
    if (d > 0) parts += "$d ngày"
    if (h > 0) parts += "$h giờ"
    if (m > 0 || parts.isEmpty()) parts += "$m phút"
    return parts.joinToString(" ")
}

fun fmtDuration(min: Int): String = fmtDuration(min.toLong())

/** Thời điểm tuyệt đối (phút cục bộ) -> "dd/MM/yyyy HH:mm". */
fun fmtDateTime(abs: Long): String =
    LocalDate.ofEpochDay(Math.floorDiv(abs, 1440L)).format(DATE_FMT) + " " +
        fmtTime(Math.floorMod(abs, 1440L).toInt())

/** Như fmtDateTime nhưng bỏ năm: "dd/MM HH:mm". */
fun fmtDateTimeShort(abs: Long): String =
    LocalDate.ofEpochDay(Math.floorDiv(abs, 1440L)).format(DAY_MONTH_FMT) + " " +
        fmtTime(Math.floorMod(abs, 1440L).toInt())

/** Tên việc kèm số sao (không có sao thì chỉ tên). */
fun titleWithStars(title: String, stars: Int): String =
    if (stars > 0) title + "  " + "★".repeat(stars) else title
