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

    private val _groups = MutableStateFlow(repo.loadGroups())
    val groups: StateFlow<List<TaskGroup>> = _groups.asStateFlow()

    private val _slots = MutableStateFlow(repo.loadSlots())
    val slots: StateFlow<List<Slot>> = _slots.asStateFlow()

    private val _adhoc = MutableStateFlow(repo.loadAdhoc())
    val adhoc: StateFlow<List<Adhoc>> = _adhoc.asStateFlow()

    private val _adv = MutableStateFlow(repo.loadAdv())
    val adv: StateFlow<AdvSettings> = _adv.asStateFlow()

    private val _keepAlive = MutableStateFlow(repo.keepAlive())
    val keepAlive: StateFlow<Boolean> = _keepAlive.asStateFlow()

    init {
        // Lưu lại ngay để dữ liệu phát sinh kiểu cũ (nếu có) được chuyển sang kiểu mới
        repo.saveAdhoc(_adhoc.value)
        refresh()
    }

    fun setKeepAlive(on: Boolean) {
        repo.setKeepAlive(on)
        _keepAlive.value = on
        KeepAlive.apply(ctx)
    }

    fun setAdv(a: AdvSettings) {
        repo.saveAdv(a)
        _adv.value = a
        refresh()
    }

    fun setupShown(): Boolean = repo.setupShown()
    fun markSetupShown() = repo.markSetupShown()

    /** Mỗi khi dữ liệu đổi: cập nhật kênh thông báo và đặt lại báo thức kế tiếp theo kế hoạch. */
    private fun refresh() {
        Reminders.syncChannels(ctx, _tasks.value)
        Reminders.reschedule(ctx, AppData(_tasks.value, _slots.value, _adhoc.value, _adv.value))
        KeepAlive.apply(ctx)
    }

    // ------------------------------------------------------------ Nhóm việc

    private fun createGroup(name: String): Int {
        val g = TaskGroup(repo.nextId(), name.trim())
        val list = _groups.value + g
        _groups.value = list
        repo.saveGroups(list)
        return g.id
    }

    fun addGroup(name: String) {
        if (name.isNotBlank()) createGroup(name)
    }

    fun renameGroup(id: Int, name: String) {
        if (name.isBlank()) return
        val list = _groups.value.map { if (it.id == id) it.copy(name = name.trim()) else it }
        _groups.value = list
        repo.saveGroups(list)
    }

    fun toggleGroup(id: Int) {
        val list = _groups.value.map { if (it.id == id) it.copy(collapsed = !it.collapsed) else it }
        _groups.value = list
        repo.saveGroups(list)
    }

    /** Xóa nhóm; các việc trong nhóm không bị xóa mà chuyển sang "Chưa phân nhóm". */
    fun deleteGroup(id: Int) {
        val tasks = _tasks.value.map { if (it.groupId == id) it.copy(groupId = null) else it }
        val groups = _groups.value.filterNot { it.id == id }
        _tasks.value = tasks
        _groups.value = groups
        repo.saveTasks(tasks)
        repo.saveGroups(groups)
    }

    // ------------------------------------------------------------ Việc

    /** id == null: thêm mới; ngược lại: cập nhật. newGroupName không rỗng: tạo nhóm mới và đưa việc vào đó. */
    fun saveTask(
        id: Int?, title: String, stars: Int, remind: Boolean,
        type: ReminderType, ringtone: String?, vib: VibLevel,
        groupId: Int?, newGroupName: String
    ) {
        val gid = if (newGroupName.isNotBlank()) createGroup(newGroupName) else groupId
        val list = _tasks.value
        val updated = if (id == null) {
            list + Task(repo.nextId(), title.trim(), stars, remind, type, ringtone, vib, gid)
        } else {
            list.map {
                if (it.id == id) it.copy(
                    title = title.trim(), stars = stars, remind = remind,
                    type = type, ringtone = ringtone, vib = vib, groupId = gid
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
        _tasks.value = t
        _slots.value = s
        repo.saveTasks(t)
        repo.saveSlots(s)
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

    fun addAdhoc(a: Adhoc) {
        val list = _adhoc.value + a.copy(id = repo.nextId())
        _adhoc.value = list
        repo.saveAdhoc(list)
        refresh()
    }

    fun updateAdhoc(a: Adhoc) {
        val list = _adhoc.value.map { if (it.id == a.id) a else it }
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
