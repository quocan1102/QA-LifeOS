package com.quantri.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

private fun openSettings(ctx: Context, primary: Intent, fallback: Intent? = null) {
    val ok = runCatching { ctx.startActivity(primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    if (!ok && fallback != null) {
        runCatching { ctx.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

@Composable
fun StatusRow(title: String, ok: Boolean, actionLabel: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(
                if (ok) "✓ Đã bật" else "✗ Chưa bật",
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
            )
        }
        if (!ok) OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}

/** Thông báo cao cấp: nhắc khi việc đang làm sắp kết thúc, đọc thành tiếng. */
@Composable
fun AdvancedSection(
    adv: AdvSettings,
    onAdv: (AdvSettings) -> Unit,
    sample: Pair<String, String?>?          // (việc hiện tại, việc kế tiếp) THẬT theo kế hoạch
) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(adv.value.toString()) }
    var modeOpen by remember { mutableStateOf(false) }
    var unitOpen by remember { mutableStateOf(false) }

    // Khi giá trị bị chỉnh lại (ví dụ ghi 950% thành 99%) thì cập nhật ô nhập
    LaunchedEffect(adv.value) {
        if (text.toIntOrNull() != adv.value) text = adv.value.toString()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Thông báo cao cấp", fontWeight = FontWeight.Medium)
                Text(
                    "Nhắc khi việc đang làm sắp kết thúc và đọc thành tiếng, để bạn tập trung hoàn thành.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(checked = adv.enabled, onCheckedChange = { onAdv(adv.copy(enabled = it)) })
        }

        if (adv.enabled) {
            Text("Thời gian dành cho việc hiện tại")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box {
                    OutlinedButton(onClick = { modeOpen = true }) {
                        Text(if (adv.remaining) "còn lại ▾" else "đã qua ▾")
                    }
                    DropdownMenu(expanded = modeOpen, onDismissRequest = { modeOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("đã qua") },
                            onClick = { onAdv(adv.copy(remaining = false)); modeOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("còn lại") },
                            onClick = { onAdv(adv.copy(remaining = true)); modeOpen = false }
                        )
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        val digits = v.filter { it.isDigit() }.take(4)
                        text = digits
                        digits.toIntOrNull()?.let { n ->
                            onAdv(adv.copy(value = Advanced.clamp(n, adv.percent)))
                        }
                    },
                    modifier = Modifier.width(88.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Box {
                    OutlinedButton(onClick = { unitOpen = true }) {
                        Text(if (adv.percent) "% ▾" else "phút ▾")
                    }
                    DropdownMenu(expanded = unitOpen, onDismissRequest = { unitOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("%") },
                            onClick = {
                                onAdv(adv.copy(percent = true, value = Advanced.clamp(adv.value, true)))
                                unitOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("phút") },
                            onClick = {
                                onAdv(adv.copy(percent = false, value = Advanced.clamp(adv.value, false)))
                                unitOpen = false
                            }
                        )
                    }
                }
            }
            Text(
                if (adv.percent) "Giá trị hợp lệ: 1 đến 99 (%)." else "Giá trị hợp lệ: 1 đến 1440 (phút).",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            if (sample != null) {
                Text(
                    "Nội dung sẽ đọc, theo kế hoạch hiện tại của bạn:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(Advanced.message(sample.first, adv, sample.second))
                OutlinedButton(onClick = { SpeakService.test(ctx, adv, sample.first, sample.second) }) {
                    Text("Nghe thử")
                }
            } else {
                Text(
                    "Chưa có việc nào trong kế hoạch nên chưa có tên việc thật để xem trước hoặc nghe thử. " +
                        "Khi có, câu nói sẽ tự dùng tên việc đang làm và việc kế tiếp, theo mẫu: " +
                        Advanced.message("[tên việc hiện tại]", adv, "[tên việc tiếp theo]"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

/** Kiểm tra và hướng dẫn bật các quyền để nhắc việc đúng giờ; cài đặt thông báo cao cấp. */
@Composable
fun SettingsDialog(
    resumeTick: Int,
    keepAlive: Boolean,
    onKeepAlive: (Boolean) -> Unit,
    adv: AdvSettings,
    onAdv: (AdvSettings) -> Unit,
    sample: Pair<String, String?>?,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val pkg = ctx.packageName
    val pkgUri = Uri.parse("package:$pkg")

    // resumeTick đổi mỗi khi quay lại app từ màn hình cài đặt hệ thống => kiểm tra lại
    val notifOk = remember(resumeTick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val exactOk = remember(resumeTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else true
    }
    val fullScreenOk = remember(resumeTick) {
        if (Build.VERSION.SDK_INT >= 34) {
            ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } else true
    }
    val batteryOk = remember(resumeTick) {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cài đặt") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AdvancedSection(adv, onAdv, sample)

                HorizontalDivider()

                Text("Chạy nền & thông báo", fontWeight = FontWeight.Bold)
                Text(
                    "Để nhắc việc đúng giờ trong mọi trường hợp (kể cả khi đã tắt app hoặc máy đang ngủ), " +
                        "hãy bật đủ các mục dưới đây.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Giữ ứng dụng chạy nền")
                        Text(
                            "Hiện một thông báo nhỏ thường trực cho biết việc kế tiếp.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(checked = keepAlive, onCheckedChange = onKeepAlive)
                }

                StatusRow("Thông báo", notifOk, "Mở cài đặt") {
                    openSettings(
                        ctx,
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
                    )
                }
                StatusRow("Báo thức chính xác", exactOk, "Cấp quyền") {
                    openSettings(
                        ctx,
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri),
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
                    )
                }
                StatusRow("Hiện màn hình báo thức khi khóa máy", fullScreenOk, "Cấp quyền") {
                    openSettings(
                        ctx,
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri),
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
                    )
                }
                StatusRow("Không bị tối ưu pin", batteryOk, "Cho phép") {
                    openSettings(
                        ctx,
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri),
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    )
                }

                HorizontalDivider()

                Text("Với Xiaomi, Oppo, Vivo, Realme, Samsung…", fontWeight = FontWeight.Medium)
                Text(
                    "Mở Thông tin ứng dụng rồi: đặt Pin / Tiết kiệm pin thành \"Không hạn chế\"; " +
                        "bật \"Tự khởi động\" (Autostart); và khóa app trong màn hình đa nhiệm " +
                        "(biểu tượng ổ khóa hoặc kéo xuống) để hệ thống không tự dọn.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = {
                        openSettings(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
                    },
                    modifier = Modifier.padding(top = 2.dp)
                ) { Text("Mở thông tin ứng dụng") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Xong") } }
    )
}
