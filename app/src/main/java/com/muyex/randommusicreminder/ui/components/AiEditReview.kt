package com.muyex.randommusicreminder.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muyex.randommusicreminder.ai.AiEditProposal

@Composable
fun AiEditReview(proposal: AiEditProposal, busy: Boolean, onApply: () -> Unit, onDismiss: () -> Unit) {
    var confirming by remember(proposal) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("核对修改 · ${proposal.after.size} 项", style = MaterialTheme.typography.titleLarge)
            proposal.before.zip(proposal.after).forEach { (before, after) ->
                Text("#${before.event.id} ${before.event.title}", style = MaterialTheme.typography.titleMedium)
                val a = before.event; val b = after.event
                val changes = buildList {
                    if (a.title != b.title) add("标题：${a.title} → ${b.title}")
                    if (a.enabled != b.enabled) add("状态：${if (a.enabled) "启用" else "停用"} → ${if (b.enabled) "启用" else "停用"}")
                    if (a.localTime != b.localTime) add("开始/事件时刻：${a.localTime} → ${b.localTime}")
                    if (a.windowEndTime != b.windowEndTime || a.windowCrossesMidnight != b.windowCrossesMidnight) add("结束：${if(a.windowCrossesMidnight) "次日 " else ""}${a.windowEndTime} → ${if(b.windowCrossesMidnight) "次日 " else ""}${b.windowEndTime}")
                    if (a.windowDurationSeconds != b.windowDurationSeconds) add("主提醒时长：${a.windowDurationSeconds}秒 → ${b.windowDurationSeconds}秒")
                    if (a.windowSnoozeMinutes != b.windowSnoozeMinutes) add("稍后：${a.windowSnoozeMinutes ?: "关闭"} → ${b.windowSnoozeMinutes ?: "关闭"}")
                    before.rules.zip(after.rules).forEachIndexed { index, (old, new) ->
                        if (old.ringDurationSeconds != new.ringDurationSeconds) add("提醒点${index+1}时长：${old.ringDurationSeconds}秒 → ${new.ringDurationSeconds}秒")
                        if (old.snoozeMinutes != new.snoozeMinutes) add("提醒点${index+1}稍后：${old.snoozeMinutes ?: "关闭"} → ${new.snoozeMinutes ?: "关闭"}")
                    }
                }
                Text(changes.joinToString("\n"), style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
            }
            Text("应用前再次检查旧值；有任何一项已改变，整批停止。成功修改保留操作记录，撤销也会检查后续改动。", style = MaterialTheme.typography.bodySmall)
            Button(enabled = !busy && proposal.after.isNotEmpty(), onClick = { confirming = true }) { Text("应用以上修改") }
            TextButton(enabled = !busy, onClick = onDismiss) { Text("放弃草稿") }
        }
    }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("应用 ${proposal.after.size} 项修改？") }, text = { Text("仅修改上方列出的事件；未来排程会重算。已发生的时间窗不会重新响铃。") },
        confirmButton = { TextButton(onClick = { confirming = false; onApply() }) { Text("确认应用") } }, dismissButton = { TextButton(onClick = { confirming = false }) { Text("返回核对") } })
}
