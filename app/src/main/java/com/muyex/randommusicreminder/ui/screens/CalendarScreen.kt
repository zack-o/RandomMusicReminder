package com.muyex.randommusicreminder.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import com.muyex.randommusicreminder.domain.calendar.*
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(days: List<CalendarOverride>, upcoming: Map<LocalDate, Int>, busy: Boolean,
    onSet: (List<LocalDate>, CalendarDayType?, String) -> Unit, modifier: Modifier = Modifier) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var note by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) }
    var selectionError by remember { mutableStateOf<String?>(null) }
    val overrides = days.associateBy { it.date }
    val start = month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value - 1).toLong())
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("日历", style = MaterialTheme.typography.headlineSmall)
            Text("固定提醒按触发日；时间窗和轮班按开始日归属，补充不重复计数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("● 放假 / 周末", color = calendarTint(CalendarDayKind.HOLIDAY).second, style = MaterialTheme.typography.labelMedium)
                Text("● 调休补班", color = calendarTint(CalendarDayKind.MAKEUP).second, style = MaterialTheme.typography.labelMedium)
                Text("● 自定义", color = calendarTint(CalendarDayKind.CUSTOM_REST).second, style = MaterialTheme.typography.labelMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = month > YearMonth.of(1900, 1), onClick = { monthText = month.minusMonths(1).toString() }) { Text("上月") }
                Text("${month.year} 年 ${month.monthValue} 月", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(enabled = month < YearMonth.of(2200, 12), onClick = { monthText = month.plusMonths(1).toString() }) { Text("下月") }
            }
            Row { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(it) } } }
            (0 until 6).forEach { week ->
                Row {
                    (0 until 7).forEach { day ->
                        val date = start.plusDays((week * 7 + day).toLong())
                        val custom = overrides[date]
                        val label = calendarDayLabel(date, custom)
                        val tint = calendarTint(label.kind)
                        val chosen = date.toString() in selected
                        Surface(modifier = Modifier.weight(1f).padding(2.dp).heightIn(min = 56.dp).semantics {
                            contentDescription = "$date ${label.name} ${label.badge}"
                            this.selected = chosen
                        }.clickable(enabled = !busy && date.year in 1900..2200) {
                            if (!chosen && selected.size >= 366) selectionError = "每次最多选择366天，请先保存或减少选择"
                            else { selected = if (chosen) selected - date.toString() else selected + date.toString(); selectionError = null }
                        }, shape = MaterialTheme.shapes.small,
                            border = if (chosen || date == LocalDate.now()) BorderStroke(if (chosen) 2.dp else 1.dp, MaterialTheme.colorScheme.primary) else null,
                            color = if (chosen) MaterialTheme.colorScheme.primaryContainer else tint.first.copy(alpha = if (date.month == month.month) 1f else .35f)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                val ink = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer else tint.second
                                Text(date.dayOfMonth.toString(), color = ink, style = MaterialTheme.typography.titleMedium)
                                Text(if (label.kind == CalendarDayKind.HOLIDAY) label.name else label.badge, color = ink, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if ((upcoming[date] ?: 0) > 0) Text("${upcoming[date]}提醒", color = ink, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        item {
            Text("已选 ${selected.size} 天（可跨月多选）", style = MaterialTheme.typography.titleMedium)
            selectionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            FlowRow {
                TextButton(onClick = { selected = emptyList() }) { Text("清空选择") }
                TextButton(onClick = { monthText = YearMonth.now().toString(); selected = listOf(LocalDate.now().toString()) }) { Text("今天") }
                TextButton(onClick = { selected = (1..month.lengthOfMonth()).map { month.atDay(it).toString() } }) { Text("选择整月") }
            }
            OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text("本次设置备注（可选）") }, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("REST" to "设为休息日", "WORK" to "设为工作日", "DEFAULT" to "恢复默认").forEach { (value, label) ->
                    OutlinedButton(enabled = selected.isNotEmpty() && !busy, onClick = { pending = value }) { Text(label) }
                }
            }
            Text("边框表示今天或选中；假=官方放假，自休/自班=个人覆盖，—=官方安排未知。官方仅覆盖2026年，其他年份未设置的日期按原规则。只影响已启用节假日策略的事件。", style = MaterialTheme.typography.bodySmall)
            Text("提醒数量按已排程的时间点计算，约覆盖未来90天，不是全部未来事件数量。", style = MaterialTheme.typography.bodySmall)
        }
        selected.sorted().forEach { value ->
            item(key = value) {
                val date = LocalDate.parse(value); val custom = overrides[date]
                Text("$value：${calendarDayLabel(date, custom).name}；待提醒 ${upcoming[date] ?: 0} 项主提醒/时间点", style = MaterialTheme.typography.bodyMedium)
                if (custom != null) Text("${custom.source}　${custom.note}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    pending?.let { action ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text("确认修改 ${selected.size} 个日期？") },
            text = { Text("${selected.sorted().take(10).joinToString("、")}${if (selected.size > 10) " 等" else ""}\n${if (action == "DEFAULT") "移除所选自定义覆盖，恢复默认日历" else if (action == "REST") "覆盖为休息日" else "覆盖为工作日"}。已有覆盖也会修改，未来提醒将重排。") },
            confirmButton = { TextButton(onClick = { onSet(selected.map(LocalDate::parse), if (action == "DEFAULT") null else CalendarDayType.valueOf(action), note); pending = null }) { Text("确认保存") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } })
    }
}

@Composable
private fun calendarTint(kind: CalendarDayKind): Pair<Color, Color> {
    val dark = MaterialTheme.colorScheme.background.luminance() < .3f
    return when (kind) {
        CalendarDayKind.HOLIDAY, CalendarDayKind.WEEKEND -> if (dark) Color(0xFF4A2529) to Color(0xFFFFB5B9) else Color(0xFFFFE9E9) to Color(0xFFA0393D)
        CalendarDayKind.MAKEUP -> if (dark) Color(0xFF1D3552) to Color(0xFFB0D2FF) else Color(0xFFE5F0FF) to Color(0xFF245F96)
        CalendarDayKind.CUSTOM_REST, CalendarDayKind.CUSTOM_WORK -> if (dark) Color(0xFF39304D) to Color(0xFFD6C3FA) else Color(0xFFF0E8FC) to Color(0xFF72579B)
        else -> MaterialTheme.colorScheme.surfaceContainerLow to MaterialTheme.colorScheme.onSurfaceVariant
    }
}
