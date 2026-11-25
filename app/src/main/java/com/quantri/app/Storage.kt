package com.quantri.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AppData(val tasks: List<Task>, val slots: List<Slot>, val adhoc: List<Adhoc>)

class Repository(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("quantri", Context.MODE_PRIVATE)

    fun load() = AppData(loadTasks(), loadSlots(), loadAdhoc())

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
                    .getOrDefault(VibLevel.MEDIUM)
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
            )
        }
        prefs.edit().putString("tasks", arr.toString()).apply()
    }

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

    fun loadAdhoc(): List<Adhoc> {
        val arr = JSONArray(prefs.getString("adhoc", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Adhoc(
                id = o.getInt("id"),
                taskId = o.getInt("taskId"),
                date = o.getLong("date"),
                startMin = o.getInt("startMin"),
                durationMin = o.getInt("durationMin")
            )
        }
    }

    fun saveAdhoc(list: List<Adhoc>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("taskId", a.taskId)
                    .put("date", a.date)
                    .put("startMin", a.startMin)
                    .put("durationMin", a.durationMin)
            )
        }
        prefs.edit().putString("adhoc", arr.toString()).apply()
    }

    fun nextId(): Int {
        val n = prefs.getInt("next_id", 1)
        prefs.edit().putInt("next_id", n + 1).apply()
        return n
    }

    fun lastFired(): Long = prefs.getLong("last_fired", 0L)
    fun setLastFired(v: Long) {
        prefs.edit().putLong("last_fired", v).apply()
    }
}
