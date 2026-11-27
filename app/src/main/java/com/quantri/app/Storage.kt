package com.quantri.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AppData(
    val tasks: List<Task>,
    val slots: List<Slot>,
    val adhoc: List<Adhoc>,
    val adv: AdvSettings = AdvSettings()
)

class Repository(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("quantri", Context.MODE_PRIVATE)

    fun load() = AppData(loadTasks(), loadSlots(), loadAdhoc(), loadAdv())

    // ------------------------------------------------------------ Việc

    fun loadTasks(): List<Task> {
        val arr = JSONArray(prefs.getString("tasks", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Task(
                id = o.getInt("id"),
                title = o.getString("title"),
                stars = o.getInt("stars"),
                remind = o.getBoolean("remind"),
                type = runCatching { ReminderType.valueOf(o.getString("type")) }
                    .getOrDefault(ReminderType.NOTIFY),
                ringtone = o.optString("ringtone", "").ifEmpty { null },
                vib = runCatching { VibLevel.valueOf(o.optString("vib", "MEDIUM")) }
                    .getOrDefault(VibLevel.MEDIUM),
                groupId = o.optInt("groupId", 0).let { if (it == 0) null else it }
            )
        }
    }

    fun saveTasks(tasks: List<Task>) {
        val arr = JSONArray()
        tasks.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("stars", t.stars)
                    .put("remind", t.remind)
                    .put("type", t.type.name)
                    .put("ringtone", t.ringtone ?: "")
                    .put("vib", t.vib.name)
                    .put("groupId", t.groupId ?: 0)
            )
        }
        prefs.edit().putString("tasks", arr.toString()).apply()
    }

    // ------------------------------------------------------------ Nhóm việc

    fun loadGroups(): List<TaskGroup> {
        val arr = JSONArray(prefs.getString("groups", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TaskGroup(o.getInt("id"), o.getString("name"), o.optBoolean("collapsed", false))
        }
    }

    fun saveGroups(groups: List<TaskGroup>) {
        val arr = JSONArray()
        groups.forEach { g ->
            arr.put(JSONObject().put("id", g.id).put("name", g.name).put("collapsed", g.collapsed))
        }
        prefs.edit().putString("groups", arr.toString()).apply()
    }

    // ------------------------------------------------------------ Thời khóa biểu

    fun loadSlots(): List<Slot> {
        val arr = JSONArray(prefs.getString("slots", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Slot(
                id = o.getInt("id"),
                taskId = o.getInt("taskId"),
                day = o.getInt("day"),
                startMin = o.getInt("startMin"),
                durationMin = o.getInt("durationMin")
            )
        }
    }

    fun saveSlots(slots: List<Slot>) {
        val arr = JSONArray()
        slots.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("taskId", s.taskId)
                    .put("day", s.day)
                    .put("startMin", s.startMin)
                    .put("durationMin", s.durationMin)
            )
        }
        prefs.edit().putString("slots", arr.toString()).apply()
    }

    // ------------------------------------------------------------ Phát sinh

    /** Đọc việc phát sinh; tự chuyển dữ liệu kiểu cũ (gắn với một Việc, trong một ngày) sang kiểu mới. */
    fun loadAdhoc(): List<Adhoc> {
        val arr = JSONArray(prefs.getString("adhoc", "[]"))
        val tasks by lazy { loadTasks() }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            if (o.has("title")) {
                Adhoc(
                    id = o.getInt("id"),
                    title = o.getString("title"),
                    startDate = o.getLong("startDate"),
                    startMin = o.getInt("startMin"),
                    endDate = o.getLong("endDate"),
                    endMin = o.getInt("endMin"),
                    remind = o.getBoolean("remind"),
                    type = runCatching { ReminderType.valueOf(o.getString("type")) }
                        .getOrDefault(ReminderType.NOTIFY),
                    ringtone = o.optString("ringtone", "").ifEmpty { null },
                    vib = runCatching { VibLevel.valueOf(o.optString("vib", "MEDIUM")) }
                        .getOrDefault(VibLevel.MEDIUM)
                )
            } else {
                val t = tasks.firstOrNull { it.id == o.getInt("taskId") }
                val date = o.getLong("date")
                val start = o.getInt("startMin")
                val end = minOf(start + o.getInt("durationMin"), 1439)
                Adhoc(
                    id = o.getInt("id"),
                    title = t?.title ?: "(việc đã xóa)",
                    startDate = date, startMin = start,
                    endDate = date, endMin = maxOf(end, start + 1).coerceAtMost(1439),
                    remind = t?.remind ?: false,
                    type = t?.type ?: ReminderType.NOTIFY,
                    ringtone = t?.ringtone,
                    vib = t?.vib ?: VibLevel.MEDIUM
                )
            }
        }
    }

    fun saveAdhoc(list: List<Adhoc>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("title", a.title)
                    .put("startDate", a.startDate)
                    .put("startMin", a.startMin)
                    .put("endDate", a.endDate)
                    .put("endMin", a.endMin)
                    .put("remind", a.remind)
                    .put("type", a.type.name)
                    .put("ringtone", a.ringtone ?: "")
                    .put("vib", a.vib.name)
            )
        }
        prefs.edit().putString("adhoc", arr.toString()).apply()
    }

    // ------------------------------------------------------------ Thông báo cao cấp

    fun loadAdv(): AdvSettings = AdvSettings(
        enabled = prefs.getBoolean("adv_enabled", true),
        remaining = prefs.getBoolean("adv_remaining", false),
        value = prefs.getInt("adv_value", 90),
        percent = prefs.getBoolean("adv_percent", true)
    )

    fun saveAdv(a: AdvSettings) {
        prefs.edit()
            .putBoolean("adv_enabled", a.enabled)
            .putBoolean("adv_remaining", a.remaining)
            .putInt("adv_value", a.value)
            .putBoolean("adv_percent", a.percent)
            .apply()
    }

    // ------------------------------------------------------------ Khác

    fun nextId(): Int {
        val n = prefs.getInt("next_id", 1)
        prefs.edit().putInt("next_id", n + 1).apply()
        return n
    }

    fun keepAlive(): Boolean = prefs.getBoolean("keep_alive", true)
    fun setKeepAlive(v: Boolean) {
        prefs.edit().putBoolean("keep_alive", v).apply()
    }

    fun setupShown(): Boolean = prefs.getBoolean("setup_shown", false)
    fun markSetupShown() {
        prefs.edit().putBoolean("setup_shown", true).apply()
    }

    fun lastFired(): Long = prefs.getLong("last_fired", 0L)
    fun setLastFired(v: Long) {
        prefs.edit().putLong("last_fired", v).apply()
    }
}
