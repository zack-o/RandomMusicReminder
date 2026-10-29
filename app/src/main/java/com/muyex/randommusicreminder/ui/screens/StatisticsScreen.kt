package com.muyex.randommusicreminder.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muyex.randommusicreminder.domain.model.OccurrenceKind
import com.muyex.randommusicreminder.domain.model.RingSessionState
import com.muyex.randommusicreminder.domain.model.RingStopReason
import com.muyex.randommusicreminder.domain.ringing.RingHistoryEntry
import com.muyex.randommusicreminder.domain.statistics.DailyUsage
import com.muyex.randommusicreminder.domain.statistics.usageWithin
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun StatisticsScreen(daily: List<DailyUsage>, history: List<RingHistoryEntry>, enabled: Boolean,
    onEnabled: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var range by rememberSaveable { mutableIntStateOf(7) }
    var includeTests by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val first = if (range == 0) null else today.minusDays(range.toLong() - 1)
    val usage = usageWithin(daily, today, range.takeIf { it > 0 })
    val rings = history.filter {
        val date = Instant.ofEpochMilli(it.startedAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        date <= today && (first == null || date >= first) && (includeTests || it.kind != OccurrenceKind.TEST)
    }
    val starts = usage.sumOf { it.launches }
    val foreground = usage.sumOf { it.foregroundMillis }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("本地使用与提醒分析", style = MaterialTheme.typography.headlineSmall)
            Text("仅统计乐醒，不读取其他应用、不上传。升级前的使用时间无法补录。", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("记录主界面使用数据", modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = onEnabled)
            }
            Text(if (enabled) "约每 15 秒保存；异常退出可能丢失最后一小段。" else "已暂停新增使用数据，保留已有统计；响铃历史仍正常记录。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90, 0).forEach { days ->
                    FilterChip(selected = range == days, onClick = { range = days }, label = { Text(if (days == 0) "全部" else "近 $days 天") })
                }
            }
        }
        item {
            StatisticsCard("使用概览", listOf(
                "主界面可见时长：${durationLabel(foreground)}",
                "前台会话次数：$starts 次",
                "有记录的日期：${usage.size} 天",
                "平均每次会话：${if (starts == 0) "暂无数据" else durationLabel(foreground / starts)}（按区间汇总估算）",
            ))
            Text("会话指打开主界面或从后台返回；旋转重建不另计。后台播放和锁屏响铃页不计入主界面时长。",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeTests, onCheckedChange = { includeTests = it })
                Text("响铃分析包含低音量试响")
            }
            StatisticsCard("响铃会话（不是任务完成量）", listOf(
                "已开始：${rings.size} 次，合并会话按一次计算",
                "手动停止：${rings.count { it.stopReason == RingStopReason.USER }} 次",
                "到时停止：${rings.count { it.stopReason == RingStopReason.TIMEOUT }} 次",
                "稍后处理：${rings.count { it.stopReason == RingStopReason.SNOOZED }} 次",
                "新提醒接替：${rings.count { it.stopReason == RingStopReason.REPLACED }} 次",
                "异常或中断：${rings.count { it.state == RingSessionState.FAILED }} 次",
            ))
        }
        item {
            val songs = rings.mapNotNull { it.musicName }.groupingBy { it }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(5)
            StatisticsCard("常用音乐", if (songs.isEmpty()) listOf("暂无已记录的播放音乐") else songs.map { "${it.key}：${it.value} 次" })
            Text("按历史中的音乐名称汇总，可能包含兜底铃声；不是播放完整歌曲的次数。", style = MaterialTheme.typography.bodySmall)
        }
        item { Text("每日使用明细", style = MaterialTheme.typography.titleMedium) }
        if (usage.isEmpty()) item { Text("这个区间还没有使用记录。开启记录后使用主界面，数据会在本地累积。") }
        items(usage, key = { it.date.toString() }) { row ->
            Text("${row.date}　${row.launches} 次　${durationLabel(row.foregroundMillis)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatisticsCard(title: String, lines: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

private fun durationLabel(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1_000
    return when {
        seconds < 60 -> "$seconds 秒"
        seconds < 3_600 -> "${seconds / 60} 分 ${seconds % 60} 秒"
        else -> "${seconds / 3_600} 小时 ${seconds % 3_600 / 60} 分"
    }
}
