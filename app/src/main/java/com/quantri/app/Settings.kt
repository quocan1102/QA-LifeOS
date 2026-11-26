package com.quantri.app

import android.app.AlarmManager
import android.content.Context
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

/** Kiểm tra và hướng dẫn bật các quyền để nhắc việc đúng giờ ngay cả khi tắt app. */
@Composable
fun SettingsDialog(
    resumeTick: Int,
    keepAlive: Boolean,
    onKeepAlive: (Boolean) -> Unit,
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
        title = { Text("Chạy nền & thông báo") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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

                HorizontalDivider()

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
