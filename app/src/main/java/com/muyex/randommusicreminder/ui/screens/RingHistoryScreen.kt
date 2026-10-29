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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muyex.randommusicreminder.domain.model.AlertMode
import com.muyex.randommusicreminder.domain.model.OccurrenceKind
import com.muyex.randommusicreminder.domain.model.RingSessionState
import com.muyex.randommusicreminder.domain.model.RingStopReason
import com.muyex.randommusicreminder.domain.ringing.RingHistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RingHistoryScreen(entries: List<RingHistoryEntry>, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("全部") }
    val visible = entries.filter { entry ->
        val matchesText = query.isBlank() || entry.title.contains(query.trim(), ignoreCase = true) ||
            entry.musicName.orEmpty().contains(query.trim(), ignoreCase = true)
        val matchesFilter = when (filter) {
            "已结束" -> entry.state == RingSessionState.COMPLETED
            "稍后提醒" -> entry.stopReason == RingStopReason.SNOOZED
            "异常" -> entry.state == RingSessionState.FAILED
            "试响" -> entry.kind == OccurrenceKind.TEST
            else -> true
        }
        matchesText && matchesFilter
    }
    val zone = ZoneId.systemDefault()
    val grouped = visible.groupBy { Instant.ofEpochMilli(it.startedAtEpochMillis).atZone(zone).toLocalDate() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("每次提醒，都有记录", style = MaterialTheme.typography.headlineSmall)
            Text(
                "记录事件、实际播放的音乐和处理结果；试响也会单独标注。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索事件或音乐") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("全部", "已结束", "稍后提醒", "异常", "试响").forEach { label ->
                    FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) })
                }
            }
            Text("${visible.size} 条记录", style = MaterialTheme.typography.labelMedium)
        }
        if (visible.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.History, contentDescription = null)
                        Text(if (entries.isEmpty()) "还没有响铃历史" else "没有符合条件的记录")
                        Text(
                            if (entries.isEmpty()) "提醒开始后会自动记录，无需手动保存。" else "试试其他关键词或筛选条件。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        grouped.forEach { (date, records) ->
            item(key = "date-$date") {
                Text(dateLabel(date), style = MaterialTheme.typography.titleSmall)
            }
            items(records, key = { it.sessionId }) { entry -> RingHistoryCard(entry, zone) }
        }
    }
}

@Composable
private fun RingHistoryCard(entry: RingHistoryEntry, zone: ZoneId) {
    val clock = DateTimeFormatter.ofPattern("HH:mm:ss")
    fun time(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(zone).format(clock)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entry.timingSummary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                entry.resultLabel,
                color = if (entry.state == RingSessionState.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                style = MaterialTheme.typography.labelLarge,
            )
            val source = when (entry.kind) {
                OccurrenceKind.TEST -> "低音量试响"
                OccurrenceKind.SNOOZE -> "稍后提醒"
                OccurrenceKind.ORIGINAL -> "正常提醒"
            }
            val mode = if (entry.alertMode == AlertMode.STRONG) "强提醒" else "温和提醒"
            Text(
                "$source · $mode" + if (entry.eventCount > 1) " · ${entry.eventCount} 个事件合并" else "",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("开始 ${time(entry.startedAtEpochMillis)} · 计划 ${time(entry.scheduledAtEpochMillis)}")
            Text("音乐：${entry.musicName ?: "未记录或未开始播放"}", style = MaterialTheme.typography.bodyMedium)
            val duration = entry.durationMillis?.let { millis ->
                if (millis < 1_000) "不足 1 秒" else {
                    val seconds = millis / 1_000
                    if (seconds < 60) "$seconds 秒" else "${seconds / 60} 分 ${seconds % 60} 秒"
                }
            }
            Text(
                when {
                    entry.endedAtEpochMillis != null -> "结束 ${time(entry.endedAtEpochMillis)} · 会话时长 $duration"
                    entry.state == RingSessionState.ACTIVE -> "正在提醒"
                    else -> "结束时间未记录"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun dateLabel(date: LocalDate): String = when (date) {
    LocalDate.now() -> "今天"
    LocalDate.now().minusDays(1) -> "昨天"
    else -> date.format(DateTimeFormatter.ofPattern("yyyy 年 MM 月 dd 日"))
}
