package com.quantri.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

val StarColor = Color(0xFFF5A623)

class MainActivity : ComponentActivity() {
    // Tăng mỗi lần quay lại app (vd. từ màn hình cài đặt hệ thống) để cập nhật trạng thái quyền.
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1E5AA8))) {
                Surface(modifier = Modifier.fillMaxSize()) { AppRoot(resumeTick) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(resumeTick: Int, vm: MainViewModel = viewModel()) {
    val tasks by vm.tasks.collectAsState()
    val slots by vm.slots.collectAsState()
    val adhoc by vm.adhoc.collectAsState()
    val adv by vm.adv.collectAsState()

    var tab by rememberSaveable { mutableStateOf(0) }
    var planDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var planView by rememberSaveable { mutableStateOf(0) }       // 0 = ngày, 1 = tuần, 2 = tháng
    var adhocDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var adhocMonth by rememberSaveable {
        mutableStateOf(LocalDate.now().withDayOfMonth(1).toEpochDay())
    }

    var showAbout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val keepAlive by vm.keepAlive.collectAsState()
    // Tên việc đang làm và việc kế tiếp THẬT theo kế hoạch, dùng cho xem trước / nghe thử thông báo cao cấp
    val sample = remember(tasks, slots, adhoc, resumeTick) {
        Advanced.currentAndNext(tasks, slots, adhoc, System.currentTimeMillis())
    }

    val permLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // Lần mở đầu tiên: hiện hướng dẫn bật chạy nền
    LaunchedEffect(Unit) {
        if (!vm.setupShown()) {
            showSettings = true
            vm.markSetupShown()
        }
    }

    val titles = listOf("Kế hoạch", "Thời khóa biểu", "Phát sinh", "Danh sách việc")
    val navItems = listOf(
        "Kế hoạch" to Icons.Default.Home,
        "Thời khóa biểu" to Icons.Default.DateRange,
        "Phát sinh" to Icons.Default.Warning,
        "Danh sách việc" to Icons.Default.Check
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titles[tab]) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Cài đặt")
                    }
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Giới thiệu")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                navItems.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label, fontSize = 10.sp, maxLines = 1, softWrap = false) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> PlanScreen(
                    tasks, slots, adhoc,
                    dayEpoch = planDay, view = planView,
                    onDay = { planDay = it }, onView = { planView = it }
                )
                1 -> TimetableScreen(tasks, slots, vm)
                2 -> AdhocScreen(
                    tasks, slots, adhoc, vm,
                    selectedEpoch = adhocDay, onSelect = { adhocDay = it },
                    monthEpoch = adhocMonth, onMonth = { adhocMonth = it }
                )
                else -> TasksScreen(tasks, vm)
            }
        }
    }

    if (showAbout) AboutDialog { showAbout = false }
    if (showSettings) {
        SettingsDialog(
            resumeTick = resumeTick,
            keepAlive = keepAlive,
            onKeepAlive = { vm.setKeepAlive(it) },
            adv = adv,
            onAdv = { vm.setAdv(it) },
            sample = sample,
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember {
        runCatching {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            @Suppress("DEPRECATION")
            val build = info.versionCode
            info.versionName + " (bản dựng " + build + ")"
        }.getOrNull() ?: ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("QA LifeOS") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Hệ điều hành cuộc sống",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Làm chủ thời gian. Thiết kế cuộc sống. Kiến tạo phiên bản tốt hơn của chính mình.",
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "QA LifeOS là hệ thống quản lý cá nhân toàn diện, giúp bạn chủ động tổ chức và điều hành " +
                        "cuộc sống thông qua việc lập kế hoạch khoa học, quản lý lịch trình và theo dõi quá trình thực hiện."
                )
                Text(
                    "Thay vì để công việc và những việc phát sinh chi phối thời gian, bạn có thể thiết kế trước " +
                        "từng ngày, xác định thứ tự ưu tiên, phân bổ thời gian hợp lý và biến những mục tiêu " +
                        "thành hành động cụ thể."
                )
                Text(
                    "QA LifeOS không chỉ giúp bạn biết mình cần làm gì, mà còn giúp bạn duy trì kỷ luật, " +
                        "theo sát kế hoạch, đánh giá kết quả và liên tục cải thiện cách sử dụng thời gian."
                )
                Text(
                    "Đừng chỉ quản lý công việc. Hãy làm chủ cuộc sống của bạn.",
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                Text("Nhà phát triển: Quốc Ân", style = MaterialTheme.typography.bodySmall)
                if (version.isNotEmpty()) {
                    Text(
                        "Phiên bản $version",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
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
            onSave = { title, stars, remind, type, ringtone, vib ->
                vm.saveTask(editing?.id, title, stars, remind, type, ringtone, vib)
                showDialog = false
            }
        )
    }

    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Xóa việc?") },
            text = {
                Text("\"${t.title}\" cùng các khung giờ và việc phát sinh của nó sẽ bị xóa.")
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteTask(t.id); deleting = null }) { Text("Xóa") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Hủy") } }
        )
    }
}

@Composable
fun TaskCard(t: Task, onEdit: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    val ringtoneName = remember(t.ringtone) { ringtoneTitle(ctx, t.ringtone) }
    val info = when {
        !t.remind -> "🔕 Không nhắc"
        t.type == ReminderType.NOTIFY -> "🔔 Chỉ hiện thông báo"
        t.type == ReminderType.VIBRATE -> "🔔 Rung · mức ${t.vib.label.lowercase()}"
        else -> "🔔 Chuông báo · $ringtoneName · rung ${t.vib.label.lowercase()}"
    }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(t.title, fontWeight = FontWeight.Medium)
                Stars(t.stars)
                Text(
                    info,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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
    onSave: (String, Int, Boolean, ReminderType, String?, VibLevel) -> Unit
) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(task?.title ?: "") }
    var stars by remember { mutableIntStateOf(task?.stars ?: 3) }
    var remind by remember { mutableStateOf(task?.remind ?: true) }
    var type by remember { mutableStateOf(task?.type ?: ReminderType.NOTIFY) }
    var ringtone by remember { mutableStateOf(task?.ringtone) }
    var vib by remember { mutableStateOf(task?.vib ?: VibLevel.MEDIUM) }
    val ringtoneName = remember(ringtone) { ringtoneTitle(ctx, ringtone) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            ringtone = uri?.toString()
        }
    }

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

                    if (type == ReminderType.ALARM) {
                        Text("Nhạc chuông")
                        OutlinedButton(onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Chọn nhạc chuông")
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    ringtone?.let { Uri.parse(it) }
                                )
                            }
                            picker.launch(intent)
                        }) {
                            Text(ringtoneName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    if (type != ReminderType.NOTIFY) {
                        Text("Mức độ rung")
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            VibLevel.entries.forEach { v ->
                                FilterChip(
                                    selected = vib == v,
                                    onClick = { vib = v },
                                    label = { Text(v.label) }
                                )
                            }
                        }
                    }

                    OutlinedButton(onClick = {
                        Reminders.test(
                            ctx,
                            Task(-1, title.ifBlank { "Thử nhắc nhở" }, stars, true, type, ringtone, vib)
                        )
                    }) { Text("Thử ngay") }

                    Text(
                        "Nhắc theo Kế hoạch trong ngày, vào giờ bắt đầu của việc này (từ Thời khóa biểu hoặc Phát sinh). " +
                            "Chuông báo và Rung sẽ kêu/rung liên tục như cuộc gọi đến cho tới khi bạn bấm Tắt " +
                            "(tối đa 10 phút), kể cả khi tắt màn hình hoặc chưa mở app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(title, stars, remind, type, ringtone, vib) }
            ) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}
