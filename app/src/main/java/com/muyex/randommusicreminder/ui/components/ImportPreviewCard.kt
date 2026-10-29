package com.muyex.randommusicreminder.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muyex.randommusicreminder.data.transfer.ImportPreview
import com.muyex.randommusicreminder.domain.model.MusicPoolMode

@Composable
fun ImportPreviewCard(preview: ImportPreview, busy: Boolean, fromAi: Boolean, onMerge: (Boolean) -> Unit,
    onSettings: (() -> Unit)?, onDismiss: () -> Unit) {
    var preserveEnabled by rememberSaveable(preview) { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (fromAi) "待确认的安排" else "导入预览", style = MaterialTheme.typography.titleLarge)
            Text("可新增 ${preview.events.size} 个事件、${preview.days.size} 个日期；已有事件及冲突日期保留。")
            Text("日期是自定义覆盖，不认证官方节假日；需事件启用节假日策略才生效。", style = MaterialTheme.typography.bodySmall)
            if (preview.events.isNotEmpty() || preview.days.isNotEmpty()) LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(preview.events) { item ->
                    Column {
                        Text(item.event.title, style = MaterialTheme.typography.titleMedium)
                        if(item.event.trackCompletion) Text("待办完成跟踪：开启；导入后从本机今天起生成，不含其他设备完成记录",style=MaterialTheme.typography.bodySmall)
                        Text("方式：${item.event.outputMode.label}"+(if(item.event.outputMode.vibration) "；${item.event.haptics.preset.label}，${item.event.haptics.strength.label}，间隔${item.event.haptics.repeatGapMillis}毫秒" else ""),style=MaterialTheme.typography.bodySmall)
                        if(item.event.tags.isNotEmpty()) Text("标签：${item.event.tags.joinToString("、")}",style=MaterialTheme.typography.bodySmall)
                        Text(importScheduleLabel(item.event), color = MaterialTheme.colorScheme.primary)
                        Text("${item.event.holidayPolicy.label} · ${if (item.event.musicPoolMode == MusicPoolMode.GLOBAL) "全部音乐随机" else "自选音乐"}", style = MaterialTheme.typography.bodySmall)
                        if (item.event.timingMode == com.muyex.randommusicreminder.domain.model.TimingMode.RANDOM_WINDOW) {
                            Text("时间窗 ${item.event.localTime} — ${if (item.event.windowCrossesMidnight) "次日 " else ""}${item.event.windowEndTime} · 随机主提醒1次\n${item.event.windowAlertMode} · ${item.event.windowDurationSeconds}秒 · 稍后${item.event.windowSnoozeMinutes?.toString() ?: "关闭"} · 补充${if (item.event.windowBackupEnabled) "开启" else "关闭"}", style = MaterialTheme.typography.bodySmall)
                        } else Text(item.rules.joinToString("\n", transform = ::importRuleLabel), style = MaterialTheme.typography.bodySmall)
                        if(item.event.scheduleType == com.muyex.randommusicreminder.domain.model.ScheduleType.SHIFT) item.event.shiftCycle?.days?.forEachIndexed { index,day ->
                            Text("第${index+1}天 ${day.label}："+if(!day.enabled) "不提醒" else day.times.joinToString("、") { (if(it.nextDay) "次日 " else "")+com.muyex.randommusicreminder.domain.time.ReminderClock.format(it.time) },style=MaterialTheme.typography.bodySmall)
                        }
                        item.event.dayOverrides.forEach { value -> Text("${value.date} · "+if(value.kind==com.muyex.randommusicreminder.domain.planner.DayOverrideKind.SKIP) "跳过这一天" else
                            "临时${value.day?.label}${if(value.day?.enabled==false) "（此日关闭）" else ""}："+(value.day?.times?.joinToString("、") { (if(it.nextDay) "次日 " else "")+com.muyex.randommusicreminder.domain.time.ReminderClock.format(it.time) }.orEmpty()),style=MaterialTheme.typography.bodySmall) }
                        if (item.event.note.isNotBlank()) Text(item.event.note, style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(Modifier.padding(top = 12.dp))
                    }
                }
                items(preview.days) { day -> Text("${day.date} → ${if (day.type == com.muyex.randommusicreminder.domain.calendar.CalendarDayType.REST) "休息" else "工作"}\n${day.note}") }
            }
            if (preview.warnings.isNotEmpty()) {
                Text("跳过/冲突 ${preview.warnings.size} 项", color = MaterialTheme.colorScheme.error)
                LazyColumn(Modifier.heightIn(max = 160.dp)) { items(preview.warnings) { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = preserveEnabled, onCheckedChange = { preserveEnabled = it }, enabled = !busy)
                Text("按原启用状态添加（否则全部停用）", style = MaterialTheme.typography.bodySmall)
            }
            Button(enabled = !busy && (preview.events.isNotEmpty() || preview.days.isNotEmpty()), onClick = { confirm = "data" }) { Text("确认合并事件与日期") }
            preview.data.settings?.let { settings ->
                Text("文件中的设置（独立应用）", style = MaterialTheme.typography.titleMedium)
                Text("外观：${settings.appearance.mode.label} / ${settings.appearance.palette.label} / ${settings.appearance.customColor}", style = MaterialTheme.typography.bodySmall)
                Text("温和${settings.gentleDurationSeconds}秒，强提醒${settings.strongDurationSeconds}秒，稍后${settings.defaultSnoozeMinutes}分钟/最多${settings.maxSnoozeCount}次\n试听${settings.previewOptions.durationSeconds}秒/${settings.previewOptions.volumePercent}%/起点${settings.previewOptions.startSeconds}秒\n遗漏通知${if (settings.notifyMissedReminders) "开启" else "关闭"}；使用统计${if (settings.usageStatsEnabled) "开启" else "关闭"}", style = MaterialTheme.typography.bodySmall)
                if (onSettings != null) OutlinedButton(enabled = !busy, onClick = { confirm = "settings" }) { Text("仅应用文件中的设置") }
            }
            TextButton(enabled = !busy, onClick = onDismiss) { Text("关闭预览") }
        }
    }
    confirm?.let { action -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text("确认${if (action == "data") "新增数据" else "替换设置参数"}？") },
        text = { Text(if (action == "data") "将新增预览中的可用事件和日期。新事件${if (preserveEnabled) "按原状态启用，可能开始提醒" else "全部停用"}。现有数据不覆盖；日历改变会重排未来提醒。设置不会自动应用。" else "仅替换上方列出的设置，包括使用统计开关。事件与日历不会因此合并，API Key 不受影响。") },
        confirmButton = { TextButton(onClick = { if (action == "data") onMerge(preserveEnabled) else onSettings?.invoke(); confirm = null }) { Text("确认") } },
        dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消") } }) }
}
