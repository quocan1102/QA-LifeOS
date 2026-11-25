package com.quantri.app

enum class ReminderType(val label: String) {
    ALARM("Chuông báo"),
    VIBRATE("Rung"),
    NOTIFY("Chỉ hiện thông báo")
}

data class Task(
    val id: Int,
    val title: String,
    val stars: Int,          // 1..5
    val remind: Boolean,
    val type: ReminderType
)

/** day: 1 = Thứ 2 ... 7 = Chủ nhật (giống java.time.DayOfWeek) */
data class Slot(
    val id: Int,
    val taskId: Int,
    val day: Int,
    val startMin: Int,       // phút tính từ 00:00
    val durationMin: Int
)

val DAY_LABELS = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

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
