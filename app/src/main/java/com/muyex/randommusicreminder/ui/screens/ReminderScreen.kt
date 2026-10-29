package com.muyex.randommusicreminder.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muyex.randommusicreminder.ui.ReminderListItem
import com.muyex.randommusicreminder.ui.UiAlertMode
import com.muyex.randommusicreminder.domain.organizer.EventTags
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderScreen(
    events: List<ReminderListItem>, onAdd: () -> Unit, onEdit: (Long) -> Unit,
    onToggle: (Long, Boolean) -> Unit, onTest: (Long) -> Unit,
    modifier: Modifier = Modifier, onAwake: (Long) -> Unit = {},onComplete:(Long)->Unit={},
) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("全部") }
    var selectedTags by rememberSaveable { mutableStateOf(listOf<String>()) }
    val availableTags=events.flatMap { it.tags }.distinctBy(EventTags::key).sorted()
    LaunchedEffect(availableTags) { selectedTags=selectedTags.filter { selected -> availableTags.any { EventTags.key(it)==EventTags.key(selected) } } }
    var detailsId by rememberSaveable { mutableStateOf<Long?>(null) }
    var reveal by remember(detailsId) { mutableStateOf(false) }
    var confirmAwake by remember { mutableStateOf(false) }
    val visible=events.filter { EventTags.matches(it.title,it.note,it.tags,search,selectedTags.toSet()) && when(filter) { "启用" -> it.enabled; "停用" -> !it.enabled; "随机时间窗" -> it.randomWindow; else -> true } }
    var testingEvent by remember { mutableStateOf<ReminderListItem?>(null) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日  EEEE", Locale.CHINA)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("我的提醒", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    Text(if (events.isEmpty()) "单次、周期与多时间点提醒" else "已启用 ${events.count { it.enabled }} 项，共 ${events.size} 项安排",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (events.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("暂无提醒", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("按月、按年或每隔N天安排；每件事可设置多个提醒点。",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) {
                        Icon(Icons.Outlined.AddAlarm, null, Modifier.size(20.dp))
                        Text("创建第一个提醒", Modifier.padding(start = 8.dp))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    listOf(Icons.Outlined.Schedule to "准时与提前提醒，按事件统一管理",
                        Icons.Outlined.Shuffle to "本地音乐轮换，不连续听同一首",
                        Icons.Outlined.EventAvailable to "工作日、补班与个人假期可配置").forEach { (icon, text) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            val next=events.filter { it.enabled && it.nextAtEpochMillis != null }.minByOrNull { it.nextAtEpochMillis!! }
            if (next != null) item {
                Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth().clickable { detailsId=next.id }) {
                    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("下一项安排",style=MaterialTheme.typography.labelLarge)
                        Text(next.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                        Text(next.nextReminderText,style=MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                OutlinedTextField(search,{search=it.take(500)},label={Text("搜索标题、详细事项或标签")},singleLine=true,leadingIcon={Icon(Icons.Outlined.Search,null)},modifier=Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("全部","启用","停用","随机时间窗").forEach { value -> FilterChip(filter==value,{filter=value},label={Text(value)}) }
                }
                if(availableTags.isNotEmpty()) {
                    Text("标签筛选（同时包含所选标签）",style=MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        availableTags.forEach { tag ->
                            val selected=selectedTags.any { EventTags.key(it)==EventTags.key(tag) }
                            FilterChip(selected,{selectedTags=if(selected) selectedTags.filterNot { EventTags.key(it)==EventTags.key(tag) } else selectedTags+tag},label={Text(tag)})
                        }
                    }
                }
                if(search.isNotBlank() || filter!="全部" || selectedTags.isNotEmpty()) TextButton(onClick={search="";filter="全部";selectedTags=emptyList()}) { Text("清空搜索与筛选") }
                Text("${visible.size}项安排",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (visible.isEmpty()) item { Text("没有符合条件的提醒，试试其他关键词或筛选。",modifier=Modifier.padding(vertical=24.dp)) }
            items(visible, key = { it.id }) { event ->
                ReminderCard(event, { detailsId=event.id }, { onToggle(event.id, it) }, { testingEvent = event },if(event.trackCompletion) ({onComplete(event.id)}) else null)
            }
        }
    }
    events.firstOrNull { it.id==detailsId }?.let { event ->
        AlertDialog(onDismissRequest={detailsId=null}, title={Text(event.title)},text={
            Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(event.tags.isNotEmpty()) Text(event.tags.joinToString("  ") { "#$it" },color=MaterialTheme.colorScheme.primary)
                Text(event.eventTimeText,style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
                Text(event.scheduleText);Text(event.timeText);Text(event.nextReminderText)
                if(event.note.isNotBlank()) { Text("详细事项",style=MaterialTheme.typography.titleSmall);androidx.compose.foundation.text.selection.SelectionContainer { Text(event.note) } }
                if(event.createdAtEpochMillis>0) Text("创建于 "+java.time.Instant.ofEpochMilli(event.createdAtEpochMillis).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),style=MaterialTheme.typography.bodySmall)
                if(event.updatedAtEpochMillis>0 && event.updatedAtEpochMillis!=event.createdAtEpochMillis) Text("更新于 "+java.time.Instant.ofEpochMilli(event.updatedAtEpochMillis).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),style=MaterialTheme.typography.bodySmall)
                Text("${event.outputMode.label} · ${event.alertMode.label} · ${formatDuration(event.durationSeconds)} · ${event.musicText}")
                if(event.randomWindow) {
                    Text("停止声音只结束当前提示，不会完成待办；随机时间窗连续两次可能选到相同分钟。",style=MaterialTheme.typography.bodySmall)
                    if(reveal) Text(event.selectionText ?: "暂无已安排的随机主提醒")
                    else TextButton(onClick={reveal=true}) { Text("查看本次已选时刻") }
                }
                if(event.hasActiveBackup) OutlinedButton(onClick={confirmAwake=true}) { Text("已收到 · 取消本次补充") }
                if(event.trackCompletion) OutlinedButton(onClick={detailsId=null;onComplete(event.id)}) { Text("选择本次并完成") }
                TextButton(onClick={testingEvent=event}) { Text("10 秒后测试当前提醒方式") }
            }
        },confirmButton={TextButton(onClick={detailsId=null;onEdit(event.id)}){Text("编辑提醒")}},dismissButton={TextButton(onClick={detailsId=null}){Text("关闭")}})
        if(confirmAwake) AlertDialog(onDismissRequest={confirmAwake=false},title={Text("确认已收到？")},text={Text("仅取消此事件当前时间窗内尚未触发的补充提醒，之后的窗口继续保留。")},confirmButton={TextButton(onClick={confirmAwake=false;detailsId=null;onAwake(event.id)}){Text("确认并取消本次补充")}},dismissButton={TextButton(onClick={confirmAwake=false}){Text("返回")}})
    }
    testingEvent?.let { event ->
        AlertDialog(onDismissRequest = { testingEvent = null }, title = { Text("10 秒后试响？") },
            text = { Text("使用“${event.title}”的${event.outputMode.label}方式测试，最多2秒。仅震动将驱动马达；音乐使用5%播放器音量；仅通知不发声、不震动。选曲参与洗牌轮换。") },
            confirmButton = { TextButton(onClick = { testingEvent = null; onTest(event.id) }) { Text("开始试响") } },
            dismissButton = { TextButton(onClick = { testingEvent = null }) { Text("取消") } })
    }
}

@Composable
private fun ReminderCard(event: ReminderListItem, onClick: () -> Unit, onToggle: (Boolean) -> Unit, onTest: () -> Unit,onComplete:(()->Unit)?=null) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.large,
        color = if (event.enabled) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (event.eventTimeText.isNotBlank()) Text(event.eventTimeText, fontSize = if(event.randomWindow) 26.sp else 40.sp, lineHeight = if(event.randomWindow) 34.sp else 44.sp, fontWeight = FontWeight.Medium,
                        color = if (event.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(event.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(event.scheduleText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(event.enabled, onToggle)
            }
            Text(event.nextReminderText, style = MaterialTheme.typography.bodyMedium,
                color = if (event.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            if(event.tags.isNotEmpty()) Text(event.tags.joinToString("  ") { "#$it" },style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(event.timeText, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.GraphicEq, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("${event.outputMode.label} · ${event.alertMode.label} · ${formatDuration(event.durationSeconds)} · ${event.musicText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (event.alertMode == UiAlertMode.STRONG && event.outputMode != com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode.NOTIFICATION) Text("全屏能力可用时唤醒屏幕", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            if(onComplete!=null) OutlinedButton(onClick=onComplete) { Text("完成本次…") }
            TextButton(onClick = onTest, enabled = event.enabled, contentPadding = PaddingValues(0.dp)) { Text("10 秒后测试当前提醒方式") }
        }
    }
}

private fun formatDuration(seconds: Int): String = when {
    seconds < 60 -> "$seconds 秒"
    seconds % 60 == 0 -> "${seconds / 60} 分钟"
    else -> "${seconds / 60} 分 ${seconds % 60} 秒"
}
