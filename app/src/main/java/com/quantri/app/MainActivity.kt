package com.quantri.app

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

private val StarColor = Color(0xFFF5A623)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1E5AA8))) {
                Surface(modifier = Modifier.fillMaxSize()) { AppRoot() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val tasks by vm.tasks.collectAsState()
    val slots by vm.slots.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    val permLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (tab == 0) "Danh sách việc" else "Thời khóa biểu") })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                    label = { Text("Việc") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    label = { Text("Thời khóa biểu") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (tab == 0) TasksScreen(tasks, vm) else TimetableScreen(tasks, slots, vm)
        }
    }
}

@Composable
fun Stars(n: Int) {
    Text(
        "★".repeat(n) + "☆".repeat(5 - n),
        color = StarColor,
        style = MaterialTheme.typography.titleMedium
    )
}

// ---------------------------------------------------------------- Danh sách việc

@Composable
fun TasksScreen(tasks: List<Task>, vm: MainViewModel) {
    var editing by remember { mutableStateOf<Task?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Task?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (tasks.isEmpty()) {
            Text(
                "Chưa có việc nào.\nBấm dấu + để thêm.",
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                color = Color.Gray
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks.sortedByDescending { it.stars }, key = { it.id }) { t ->
                    TaskCard(
                        t,
                        onEdit = { editing = t; showDialog = true },
                        onDelete = { deleting = t }
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = { editing = null; showDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Thêm việc") }
    }

    if (showDialog) {
        TaskDialog(
            task = editing,
            onDismiss = { showDialog = false },
            onSave = { title, stars, remind, type ->
                vm.saveTask(editing?.id, title, stars, remind, type)
                showDialog = false
            }
        )
    }

    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Xóa việc?") },
            text = { Text("\"${t.title}\" và các khung giờ của nó trong thời khóa biểu sẽ bị xóa.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteTask(t.id); deleting = null }) { Text("Xóa") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Hủy") } }
        )
    }
}

@Composable
fun TaskCard(t: Task, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(t.title, fontWeight = FontWeight.Medium)
                Stars(t.stars)
                Text(
                    if (t.remind) "🔔 ${t.type.label}" else "🔕 Không nhắc",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Sửa") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Xóa") }
        }
    }
}

@Composable
fun TaskDialog(
    task: Task?,
    onDismiss: () -> Unit,
    onSave: (String, Int, Boolean, ReminderType) -> Unit
) {
    var title by remember { mutableStateOf(task?.title ?: "") }
    var stars by remember { mutableIntStateOf(task?.stars ?: 3) }
    var remind by remember { mutableStateOf(task?.remind ?: true) }
    var type by remember { mutableStateOf(task?.type ?: ReminderType.NOTIFY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task == null) "Thêm việc" else "Sửa việc") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên việc") },
                    singleLine = true
                )
                Text("Mức độ quan trọng")
                Row {
                    (1..5).forEach { i ->
                        Text(
                            if (i <= stars) "★" else "☆",
                            color = StarColor,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.clickable { stars = i }.padding(end = 6.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nhắc nhở", Modifier.weight(1f))
                    Switch(checked = remind, onCheckedChange = { remind = it })
                }
                if (remind) {
                    ReminderType.entries.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().clickable { type = r },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = type == r, onClick = { type = r })
                            Text(r.label)
                        }
                    }
                    Text(
                        "Nhắc vào giờ bắt đầu của các khung giờ bạn xếp cho việc này trong Thời khóa biểu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(title, stars, remind, type) }
            ) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

// ---------------------------------------------------------------- Thời khóa biểu

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
    var showAdd by remember { mutableStateOf(false) }
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
                                "${fmtTime(s.startMin)} – ${fmtTime((s.startMin + s.durationMin) % 1440)}",
                                fontWeight = FontWeight.Bold
                            )
                            Text(t?.title ?: "(đã xóa)")
                            if (t != null) Stars(t.stars)
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
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Thêm khung giờ") }
    }

    if (showAdd) {
        AddSlotDialog(
            tasks = tasks,
            initialDay = day,
            onDismiss = { showAdd = false },
            onSave = { taskId, days, start, duration ->
                vm.addSlots(taskId, days, start, duration)
                showAdd = false
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
            Text("Phân bổ thời gian trong tuần", fontWeight = FontWeight.Bold)
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

@Composable
fun AddSlotDialog(
    tasks: List<Task>,
    initialDay: Int,
    onDismiss: () -> Unit,
    onSave: (taskId: Int, days: Set<Int>, startMin: Int, durationMin: Int) -> Unit
) {
    val ctx = LocalContext.current
    var taskId by remember { mutableIntStateOf(tasks.first().id) }
    var days by remember { mutableStateOf(setOf(initialDay)) }
    var startMin by remember { mutableIntStateOf(7 * 60) }
    var duration by remember { mutableIntStateOf(60) }
    var expanded by remember { mutableStateOf(false) }
    val current = tasks.firstOrNull { it.id == taskId } ?: tasks.first()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm vào thời khóa biểu") },
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

                Text("Các ngày trong tuần")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DAY_LABELS.forEachIndexed { i, label ->
                        val d = i + 1
                        FilterChip(
                            selected = d in days,
                            onClick = { days = if (d in days) days - d else days + d },
                            label = { Text(label) }
                        )
                    }
                }

                Text("Giờ bắt đầu")
                OutlinedButton(onClick = {
                    TimePickerDialog(
                        ctx,
                        { _, h, m -> startMin = h * 60 + m },
                        startMin / 60,
                        startMin % 60,
                        true
                    ).show()
                }) { Text(fmtTime(startMin)) }

                Text("Thời lượng")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { duration = (duration - 15).coerceAtLeast(15) }) {
                        Text("−15")
                    }
                    Text(fmtDuration(duration), Modifier.padding(horizontal = 12.dp))
                    OutlinedButton(onClick = { duration = (duration + 15).coerceAtMost(720) }) {
                        Text("+15")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = days.isNotEmpty(),
                onClick = { onSave(taskId, days, startMin, duration) }
            ) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}
