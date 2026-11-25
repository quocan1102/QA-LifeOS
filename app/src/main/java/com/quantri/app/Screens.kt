package com.quantri.app

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

// =============================================================== Bộ chọn Bắt đầu / Kết thúc / Thời lượng

enum class TimeField { START, END, DURATION }

/**
 * Giữ 3 giá trị (phút). Người dùng chỉ "chọn" 2 trong 3; giá trị còn lại tự được tính.
 * Hai mục được sửa gần nhất là hai mục được chọn; sửa mục thứ ba thì mục được sửa lâu nhất bị tính lại.
 * Một khung giờ không được vượt qua 24:00.
 */
class TimeRangeState(initStart: Int, initDuration: Int) {
    private val initEnd = minOf(initStart + initDuration, 1440)
    var start by mutableIntStateOf(initStart)
    var end by mutableIntStateOf(initEnd)
    var duration by mutableIntStateOf(initEnd - initStart)

    private var older = TimeField.DURATION
    private var newer = TimeField.START

    val computed: TimeField
        get() = TimeField.entries.first { it != older && it != newer }

    val valid: Boolean
        get() = duration > 0 && start >= 0 && end <= 1440

    fun set(field: TimeField, value: Int) {
        when (field) {
            TimeField.START -> start = value
            TimeField.END -> end = value
            TimeField.DURATION -> duration = value
        }
        if (field != newer) {
            older = newer
            newer = field
        }
        recompute()
    }

    private fun recompute() {
        when (computed) {
            TimeField.END -> {
                end = start + duration
                if (end > 1440) {
                    end = 1440
                    duration = end - start
                }
            }
            TimeField.DURATION -> duration = end - start
            TimeField.START -> {
                start = end - duration
                if (start < 0) {
                    start = 0
                    duration = end
                }
            }
        }
    }
}

@Composable
fun TimeRow(label: String, value: String, computed: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (computed) {
                Text("tự tính", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        OutlinedButton(onClick = onClick) { Text(value) }
    }
}

@Composable
fun TimeRangeEditor(s: TimeRangeState) {
    val ctx = LocalContext.current
    fun pick(field: TimeField, current: Int) {
        val c = minOf(current, 1439)
        TimePickerDialog(ctx, { _, h, m -> s.set(field, h * 60 + m) }, c / 60, c % 60, true).show()
    }

    Text(
        "Chọn 2 trong 3 mục (giờ bắt đầu, giờ kết thúc, thời lượng). Mục còn lại sẽ tự được tính.",
        style = MaterialTheme.typography.bodySmall,
        color = Color.Gray
    )
    TimeRow("Giờ bắt đầu", fmtTime(s.start), s.computed == TimeField.START) {
        pick(TimeField.START, s.start)
    }
    TimeRow("Giờ kết thúc", fmtTime(s.end), s.computed == TimeField.END) {
        pick(TimeField.END, s.end)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Thời lượng")
            if (s.computed == TimeField.DURATION) {
                Text("tự tính", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        Text(if (s.duration > 0) fmtDuration(s.duration) else "—", fontWeight = FontWeight.Medium)
    }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(-15, -5, 5, 15).forEach { delta ->
            OutlinedButton(onClick = {
                s.set(TimeField.DURATION, (s.duration + delta).coerceIn(5, 1440))
            }) { Text(if (delta > 0) "+$delta" else "−${-delta}") }
        }
    }
    if (!s.valid) {
        Text(
            "Giờ kết thúc phải sau giờ bắt đầu (trong cùng một ngày).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

// =============================================================== Thời khóa biểu

@Composable
fun TimetableScreen(tasks: List<Task>, slots: List<Slot>, vm: MainViewModel) {
    if (tasks.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Text(
                "Hãy thêm việc ở tab \"Việc\" trước,\nrồi quay lại đây để xếp lịch.",
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                color = Color.Gray
            )
        }
        return
    }

    val today = remember { LocalDate.now().dayOfWeek.value }
    var day by remember { mutableIntStateOf(today) }
    var editing by remember { mutableStateOf<Slot?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    val taskById = tasks.associateBy { it.id }
    val daySlots = slots.filter { it.day == day }.sortedBy { it.startMin }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DAY_LABELS.forEachIndexed { i, label ->
                        FilterChip(
                            selected = day == i + 1,
                            onClick = { day = i + 1 },
                            label = { Text(label) }
                        )
                    }
                }
            }
            if (daySlots.isEmpty()) {
                item {
                    Text(
                        "Chưa có việc nào trong ngày này.",
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
            items(daySlots, key = { it.id }) { s ->
                val t = taskById[s.taskId]
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                fmtTime(s.startMin) + " – " + fmtTime(minOf(s.startMin + s.durationMin, 1440)),
                                fontWeight = FontWeight.Bold
                            )
                            Text(t?.title ?: "(đã xóa)")
                            if (t != null) Stars(t.stars)
                        }
                        IconButton(onClick = { editing = s; showDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Sửa")
                        }
                        IconButton(onClick = { vm.deleteSlot(s.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Xóa")
                        }
                    }
                }
            }
            item { WeeklySummary(tasks, slots) }
        }
        FloatingActionButton(
            onClick = { editing = null; showDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Thêm khung giờ") }
    }

    if (showDialog) {
        SlotDialog(
            tasks = tasks,
            initial = editing,
            initialDay = day,
            onDismiss = { showDialog = false },
            onSave = { taskId, days, start, duration ->
                val ed = editing
                if (ed == null) {
                    vm.addSlots(taskId, days, start, duration)
                } else {
                    vm.updateSlot(ed.id, taskId, days.first(), start, duration)
                }
                showDialog = false
            }
        )
    }
}

@Composable
fun WeeklySummary(tasks: List<Task>, slots: List<Slot>) {
    val totals = tasks
        .map { t -> t to slots.filter { it.taskId == t.id }.sumOf { it.durationMin } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
    if (totals.isEmpty()) return

    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Phân bổ thời gian cố định trong tuần", fontWeight = FontWeight.Bold)
            totals.forEach { (t, m) ->
                Row {
                    Text(t.title, Modifier.weight(1f))
                    Text(fmtDuration(m))
                }
            }
            HorizontalDivider()
            Row {
                Text("Tổng", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(fmtDuration(totals.sumOf { it.second }), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Thêm mới (initial == null, chọn được nhiều ngày) hoặc sửa một khung giờ (chỉ chọn 1 ngày). */
@Composable
fun SlotDialog(
    tasks: List<Task>,
    initial: Slot?,
    initialDay: Int,
    onDismiss: () -> Unit,
    onSave: (taskId: Int, days: Set<Int>, startMin: Int, durationMin: Int) -> Unit
) {
    var taskId by remember { mutableIntStateOf(initial?.taskId ?: tasks.first().id) }
    var days by remember { mutableStateOf(setOf(initial?.day ?: initialDay)) }
    val time = remember { TimeRangeState(initial?.startMin ?: 7 * 60, initial?.durationMin ?: 60) }
    var expanded by remember { mutableStateOf(false) }
    val current = tasks.firstOrNull { it.id == taskId } ?: tasks.first()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Thêm vào thời khóa biểu" else "Sửa khung giờ") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Việc")
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(current.title) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        tasks.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.title) },
                                onClick = { taskId = t.id; expanded = false }
                            )
                        }
                    }
                }
                if (!current.remind) {
                    Text(
                        "Việc này đang tắt nhắc nhở nên sẽ không báo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Text(if (initial == null) "Các ngày trong tuần" else "Ngày trong tuần")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DAY_LABELS.forEachIndexed { i, label ->
                        val d = i + 1
                        FilterChip(
                            selected = d in days,
                            onClick = {
                                days = when {
                                    initial != null -> setOf(d)
                                    d in days -> days - d
                                    else -> days + d
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }

                TimeRangeEditor(time)
            }
        },
        confirmButton = {
            TextButton(
                enabled = days.isNotEmpty() && time.valid,
                onClick = { onSave(taskId, days, time.start, time.duration) }
            ) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

// =============================================================== Kế hoạch (theo ngày)

@Composable
fun PlanScreen(
    tasks: List<Task>,
    slots: List<Slot>,
    adhoc: List<Adhoc>,
    dayEpoch: Long,
    onDay: (Long) -> Unit
) {
    val date = LocalDate.ofEpochDay(dayEpoch)
    val today = LocalDate.now()
    val byId = tasks.associateBy { it.id }
    val segs = Plan.forDate(date, slots, adhoc)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onDay(dayEpoch - 1) }) { Text("‹", fontSize = 26.sp) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dayTitle(date), fontWeight = FontWeight.Bold)
                if (date == today) {
                    Text(
                        "Hôm nay",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    TextButton(onClick = { onDay(today.toEpochDay()) }) { Text("Về hôm nay") }
                }
            }
            TextButton(onClick = { onDay(dayEpoch + 1) }) { Text("›", fontSize = 26.sp) }
        }

        if (segs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    "Ngày này chưa có việc nào.\nHãy xếp lịch ở Thời khóa biểu hoặc thêm ở Phát sinh.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var maxEnd = -1
                segs.forEach { s ->
                    if (maxEnd >= 0 && s.startMin > maxEnd) {
                        val from = maxEnd
                        item {
                            Text(
                                "Trống " + fmtTime(from) + " – " + fmtTime(s.startMin) +
                                    " (" + fmtDuration(s.startMin - from) + ")",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    maxEnd = maxOf(maxEnd, s.endMin)
                    item { SegmentCard(s, byId[s.taskId]) }
                }
            }
        }
    }
}

@Composable
fun SegmentCard(s: Segment, t: Task?) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(fmtTime(s.startMin) + " – " + fmtTime(s.endMin), fontWeight = FontWeight.Bold)
                Text(t?.title ?: "(đã xóa)")
                if (t != null) Stars(t.stars)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (s.source == Source.ADHOC) "Phát sinh" else "Cố định",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (s.source == Source.ADHOC) MaterialTheme.colorScheme.primary else Color.Gray
                )
                Text(
                    fmtDuration(s.endMin - s.startMin),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                if (t != null && t.remind) Text("🔔", fontSize = 14.sp)
            }
        }
    }
}

// =============================================================== Phát sinh (lịch tháng)

@Composable
fun DayCell(
    day: Int,
    selected: Boolean,
    isToday: Boolean,
    count: Int,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .height(46.dp)
            .padding(2.dp)
            .background(if (selected) primary else Color.Transparent, shape)
            .then(if (isToday && !selected) Modifier.border(1.dp, primary, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day.toString(), color = if (selected) Color.White else Color.Unspecified)
            Text(
                if (count > 0) "●" else " ",
                fontSize = 8.sp,
                color = if (selected) Color.White else primary
            )
        }
    }
}

@Composable
fun AdhocScreen(
    tasks: List<Task>,
    slots: List<Slot>,
    adhoc: List<Adhoc>,
    vm: MainViewModel,
    selectedEpoch: Long,
    onSelect: (Long) -> Unit,
    monthEpoch: Long,
    onMonth: (Long) -> Unit
) {
    val first = LocalDate.ofEpochDay(monthEpoch)
    val offset = first.dayOfWeek.value - 1
    val total = YearMonth.from(first).lengthOfMonth()
    val rows = (offset + total + 6) / 7
    val today = LocalDate.now()
    val counts = adhoc.groupingBy { it.date }.eachCount()
    val taskById = tasks.associateBy { it.id }
    val selectedDate = LocalDate.ofEpochDay(selectedEpoch)
    val dayEntries = adhoc.filter { it.date == selectedEpoch }.sortedBy { it.startMin }

    var editing by remember { mutableStateOf<Adhoc?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onMonth(first.minusYears(1).toEpochDay()) }) { Text("«", fontSize = 22.sp) }
                    TextButton(onClick = { onMonth(first.minusMonths(1).toEpochDay()) }) { Text("‹", fontSize = 22.sp) }
                    Text(
                        "Tháng " + first.monthValue + "/" + first.year,
                        Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    TextButton(onClick = { onMonth(first.plusMonths(1).toEpochDay()) }) { Text("›", fontSize = 22.sp) }
                    TextButton(onClick = { onMonth(first.plusYears(1).toEpochDay()) }) { Text("»", fontSize = 22.sp) }
                }
            }
            item {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        DAY_LABELS.forEach { label ->
                            Text(
                                label,
                                Modifier.weight(1f),
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    for (r in 0 until rows) {
                        Row(Modifier.fillMaxWidth()) {
                            for (c in 0 until 7) {
                                val dayNum = r * 7 + c - offset + 1
                                if (dayNum < 1 || dayNum > total) {
                                    Box(Modifier.weight(1f).height(46.dp))
                                } else {
                                    val date = first.plusDays((dayNum - 1).toLong())
                                    val ep = date.toEpochDay()
                                    DayCell(
                                        day = dayNum,
                                        selected = ep == selectedEpoch,
                                        isToday = date == today,
                                        count = counts[ep] ?: 0,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onSelect(ep) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Việc phát sinh · " + dayTitle(selectedDate),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (dayEntries.isEmpty()) {
                item {
                    Text(
                        "Chưa có việc phát sinh. Bấm dấu + để thêm.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            items(dayEntries, key = { it.id }) { a ->
                val t = taskById[a.taskId]
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                fmtTime(a.startMin) + " – " + fmtTime(minOf(a.startMin + a.durationMin, 1440)),
                                fontWeight = FontWeight.Bold
                            )
                            Text(t?.title ?: "(đã xóa)")
                            if (t != null) Stars(t.stars)
                        }
                        IconButton(onClick = { editing = a; showDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Sửa")
                        }
                        IconButton(onClick = { vm.deleteAdhoc(a.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Xóa")
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { editing = null; showDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Thêm việc phát sinh") }
    }

    if (showDialog) {
        AdhocDialog(
            tasks = tasks,
            initial = editing,
            date = selectedDate,
            onDismiss = { showDialog = false },
            onSave = { taskId, newTitle, start, duration ->
                val ed = editing
                if (ed == null) {
                    vm.addAdhoc(taskId, newTitle, selectedEpoch, start, duration)
                } else {
                    vm.updateAdhoc(ed.id, taskId, newTitle, start, duration)
                }
                showDialog = false
            }
        )
    }
}

@Composable
fun AdhocDialog(
    tasks: List<Task>,
    initial: Adhoc?,
    date: LocalDate,
    onDismiss: () -> Unit,
    onSave: (taskId: Int?, newTitle: String, startMin: Int, durationMin: Int) -> Unit
) {
    var taskId by remember { mutableStateOf(initial?.taskId ?: tasks.firstOrNull()?.id) }
    var newTitle by remember { mutableStateOf("") }
    val time = remember { TimeRangeState(initial?.startMin ?: 8 * 60, initial?.durationMin ?: 60) }
    var expanded by remember { mutableStateOf(false) }
    val currentTitle = tasks.firstOrNull { it.id == taskId }?.title

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Thêm việc phát sinh" else "Sửa việc phát sinh") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(dayTitle(date), fontWeight = FontWeight.Medium)
                Text("Việc")
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(currentTitle ?: "＋ Việc mới")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        tasks.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.title) },
                                onClick = { taskId = t.id; expanded = false }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("＋ Việc mới…") },
                            onClick = { taskId = null; expanded = false }
                        )
                    }
                }
                if (currentTitle == null) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Tên việc mới") },
                        singleLine = true
                    )
                }

                TimeRangeEditor(time)

                Text(
                    "Nếu trùng giờ với Thời khóa biểu: cùng việc thì thay thế khung cố định đó, khác việc thì cắt phần trùng.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = time.valid && (currentTitle != null || newTitle.isNotBlank()),
                onClick = { onSave(taskId, newTitle, time.start, time.duration) }
            ) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}
