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

    private val _adhoc = MutableStateFlow(repo.loadAdhoc())
    val adhoc: StateFlow<List<Adhoc>> = _adhoc.asStateFlow()

    init {
        refresh()
    }

    /** Mỗi khi dữ liệu đổi: cập nhật kênh thông báo và đặt lại báo thức kế tiếp theo kế hoạch. */
    private fun refresh() {
        Reminders.syncChannels(ctx, _tasks.value)
        Reminders.reschedule(ctx, AppData(_tasks.value, _slots.value, _adhoc.value))
    }

    // ------------------------------------------------------------ Việc

    /** id == null: thêm mới; ngược lại: cập nhật. */
    fun saveTask(
        id: Int?, title: String, stars: Int, remind: Boolean,
        type: ReminderType, ringtone: String?, vib: VibLevel
    ) {
        val list = _tasks.value
        val updated = if (id == null) {
            list + Task(repo.nextId(), title.trim(), stars, remind, type, ringtone, vib)
        } else {
            list.map {
                if (it.id == id) it.copy(
                    title = title.trim(), stars = stars, remind = remind,
                    type = type, ringtone = ringtone, vib = vib
                ) else it
            }
        }
        _tasks.value = updated
        repo.saveTasks(updated)
        refresh()
    }

    fun deleteTask(id: Int) {
        val t = _tasks.value.filterNot { it.id == id }
        val s = _slots.value.filterNot { it.taskId == id }
        val a = _adhoc.value.filterNot { it.taskId == id }
        _tasks.value = t
        _slots.value = s
        _adhoc.value = a
        repo.saveTasks(t)
        repo.saveSlots(s)
        repo.saveAdhoc(a)
        refresh()
    }

    // ------------------------------------------------------------ Thời khóa biểu

    fun addSlots(taskId: Int, days: Set<Int>, startMin: Int, durationMin: Int) {
        val added = days.sorted().map { Slot(repo.nextId(), taskId, it, startMin, durationMin) }
        val s = _slots.value + added
        _slots.value = s
        repo.saveSlots(s)
        refresh()
    }

    fun updateSlot(id: Int, taskId: Int, day: Int, startMin: Int, durationMin: Int) {
        val s = _slots.value.map {
            if (it.id == id) it.copy(taskId = taskId, day = day, startMin = startMin, durationMin = durationMin)
            else it
        }
        _slots.value = s
        repo.saveSlots(s)
        refresh()
    }

    fun deleteSlot(id: Int) {
        val s = _slots.value.filterNot { it.id == id }
        _slots.value = s
        repo.saveSlots(s)
        refresh()
    }

    // ------------------------------------------------------------ Phát sinh

    /** Nếu taskId == null thì tạo việc mới tên newTitle (nhắc dạng thông báo, 3 sao). */
    private fun resolveTask(taskId: Int?, newTitle: String): Int? {
        if (taskId != null) return taskId
        val title = newTitle.trim()
        if (title.isEmpty()) return null
        val t = Task(repo.nextId(), title, 3, true, ReminderType.NOTIFY)
        val list = _tasks.value + t
        _tasks.value = list
        repo.saveTasks(list)
        return t.id
    }

    fun addAdhoc(taskId: Int?, newTitle: String, dateEpoch: Long, startMin: Int, durationMin: Int) {
        val tid = resolveTask(taskId, newTitle) ?: return
        val list = _adhoc.value + Adhoc(repo.nextId(), tid, dateEpoch, startMin, durationMin)
        _adhoc.value = list
        repo.saveAdhoc(list)
        refresh()
    }

    fun updateAdhoc(id: Int, taskId: Int?, newTitle: String, startMin: Int, durationMin: Int) {
        val tid = resolveTask(taskId, newTitle) ?: return
        val list = _adhoc.value.map {
            if (it.id == id) it.copy(taskId = tid, startMin = startMin, durationMin = durationMin) else it
        }
        _adhoc.value = list
        repo.saveAdhoc(list)
        refresh()
    }

    fun deleteAdhoc(id: Int) {
        val list = _adhoc.value.filterNot { it.id == id }
        _adhoc.value = list
        repo.saveAdhoc(list)
        refresh()
    }
}
