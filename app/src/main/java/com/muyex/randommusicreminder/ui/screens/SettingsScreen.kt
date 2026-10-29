package com.muyex.randommusicreminder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muyex.randommusicreminder.ui.PermissionHealthUi
import com.muyex.randommusicreminder.ui.SettingsUi
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: SettingsUi,
    permissionHealth: PermissionHealthUi,
    onGentleDurationChange: (Int) -> Unit,
    onStrongDurationChange: (Int) -> Unit,
    onDefaultSnoozeChange: (Int) -> Unit,
    onNotifyMissedChange: (Boolean) -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestFullScreenIntent: () -> Unit,
    onOpenBackgroundSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var gentleSeconds by remember(settings.gentleDurationSeconds) {
        mutableFloatStateOf(settings.gentleDurationSeconds.toFloat())
    }
    var strongSeconds by remember(settings.strongDurationSeconds) {
        mutableFloatStateOf(settings.strongDurationSeconds.toFloat())
    }
    val snoozeOptions = listOf(5, 10, 15)
    var snoozeIndex by remember(settings.defaultSnoozeMinutes) {
        mutableFloatStateOf(snoozeOptions.indexOf(settings.defaultSnoozeMinutes).coerceAtLeast(0).toFloat())
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "调整随机策略，并确认系统允许乐醒准时提醒你。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Shuffle, contentDescription = null)
                        Text(
                            "随机音乐",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("洗牌式随机")
                    Text(
                        text = "启用的音乐会先完整轮换一遍，再开始下一轮；相邻两次不会重复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                        Text(
                            "默认提醒行为",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("温和提醒默认 ${formatDuration(gentleSeconds.roundToInt())}")
                    Slider(
                        value = gentleSeconds,
                        onValueChange = { gentleSeconds = it },
                        onValueChangeFinished = { onGentleDurationChange(gentleSeconds.roundToInt()) },
                        valueRange = 5f..300f,
                    )
                    Text("强提醒默认 ${formatDuration(strongSeconds.roundToInt())}")
                    Slider(
                        value = strongSeconds,
                        onValueChange = { strongSeconds = it },
                        onValueChangeFinished = { onStrongDurationChange(strongSeconds.roundToInt()) },
                        valueRange = 5f..1_800f,
                    )
                    Text("稍后提醒：${snoozeOptions[snoozeIndex.roundToInt()]} 分钟")
                    Slider(
                        value = snoozeIndex,
                        onValueChange = { snoozeIndex = it },
                        onValueChangeFinished = {
                            onDefaultSnoozeChange(snoozeOptions[snoozeIndex.roundToInt()])
                        },
                        valueRange = 0f..2f,
                        steps = 1,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("补发错过的提醒", modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.notifyMissedReminders,
                            onCheckedChange = onNotifyMissedChange,
                        )
                    }
                }
            }
        }

        item {
            PermissionCard(
                health = permissionHealth,
                onRequestNotifications = onRequestNotifications,
                onRequestExactAlarm = onRequestExactAlarm,
                onRequestFullScreenIntent = onRequestFullScreenIntent,
            )
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("锁屏与后台运行", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            permissionHealth.backgroundRestricted -> "系统限制了后台运行，锁屏提醒可能延迟"
                            !permissionHealth.batteryOptimizationIgnored -> "仍受系统省电优化，需结合锁屏试响确认"
                            else -> "已豁免标准电池优化，仍需检查手机厂商的后台管理"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "在应用详情中检查通知、锁屏显示、后台运行与自启动。" +
                            "部分手机的省电策略无法由乐醒直接读取；允许通知与精确闹钟不等于后台一定畅通。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FilledTonalButton(onClick = onOpenBackgroundSettings) { Text("打开乐醒应用设置") }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String = when {
    seconds < 60 -> "${seconds} 秒"
    seconds % 60 == 0 -> "${seconds / 60} 分钟"
    else -> "${seconds / 60} 分 ${seconds % 60} 秒"
}

@Composable
private fun PermissionCard(
    health: PermissionHealthUi,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestFullScreenIntent: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (health.allReady) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = if (health.allReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Column(Modifier.padding(start = 8.dp)) {
                    Text("权限健康检查", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (health.allReady) "所有关键权限均已就绪" else "还有权限需要处理，否则提醒可能迟到",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            PermissionRow(
                title = "通知权限",
                ready = health.notificationsGranted,
                onFix = onRequestNotifications,
            )
            PermissionRow(
                title = "精确闹钟",
                ready = health.exactAlarmGranted,
                onFix = onRequestExactAlarm,
            )
            PermissionRow(
                title = "全屏强提醒",
                ready = !health.fullScreenIntentRequired || health.fullScreenIntentGranted,
                readyLabel = when {
                    health.fullScreenIntentGranted -> "已就绪"
                    !health.fullScreenIntentRequired -> "按需开启"
                    else -> "已就绪"
                },
                onFix = onRequestFullScreenIntent,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    ready: Boolean,
    readyLabel: String = "已就绪",
    onFix: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        if (ready) {
            Text(readyLabel, color = MaterialTheme.colorScheme.primary)
        } else {
            FilledTonalButton(onClick = onFix) { Text("去设置") }
        }
    }
}
