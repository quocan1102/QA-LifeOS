package com.quantri.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val ctx = app.applicationContext

    private val _tasks = MutableStateFlow(repo.loadTasks())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _slots = MutableStateFlow(repo.loadSlots())
    val slots: StateFlow<List<Slot>> = _slots.asStateFlow()

    init {
        Reminders.createChannels(ctx)
        Reminders.scheduleAll(ctx, _tasks.value, _slots.value)
    }

    /** id == null: thêm mới; ngược lại: cập nhật. */
    fun saveTask(id: Int?, title: String, stars: Int, remind: Boolean, type: ReminderType) {
        val list = _tasks.value
        val updated = if (id == null) {
            list + Task(repo.nextId(), title.trim(), stars, remind, type)
        } else {
            list.map {
                if (it.id == id) it.copy(title = title.trim(), stars = stars, remind = remind, type = type)
                else it
            }
        }
        _tasks.value = updated
        repo.saveTasks(updated)

        // Đặt lại báo thức cho các khung giờ của việc vừa sửa
        if (id != null) {
            val task = updated.firstOrNull { it.id == id }
            _slots.value.filter { it.taskId == id }.forEach { Reminders.schedule(ctx, it, task) }
        }
    }

    fun deleteTask(id: Int) {
        _slots.value.filter { it.taskId == id }.forEach { Reminders.cancel(ctx, it.id) }
        val t = _tasks.value.filterNot { it.id == id }
        val s = _slots.value.filterNot { it.taskId == id }
        _tasks.value = t
        _slots.value = s
        repo.saveTasks(t)
        repo.saveSlots(s)
    }

    fun addSlots(taskId: Int, days: Set<Int>, startMin: Int, durationMin: Int) {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
        val added = days.sorted().map { Slot(repo.nextId(), taskId, it, startMin, durationMin) }
        val s = _slots.value + added
        _slots.value = s
        repo.saveSlots(s)
        added.forEach { Reminders.schedule(ctx, it, task) }
    }

    fun deleteSlot(id: Int) {
        Reminders.cancel(ctx, id)
        val s = _slots.value.filterNot { it.id == id }
        _slots.value = s
        repo.saveSlots(s)
    }
}
