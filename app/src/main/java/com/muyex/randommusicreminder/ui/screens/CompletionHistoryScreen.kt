package com.muyex.randommusicreminder.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muyex.randommusicreminder.domain.tasks.*
import com.muyex.randommusicreminder.domain.organizer.EventTags
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CompletionHistoryScreen(records:List<TaskActionRecord>,normalEventIds:Set<Long>,busy:Boolean,onReopen:(Long)->Unit,onExport:()->Unit,modifier:Modifier=Modifier) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("全部") }
    var undo by remember { mutableStateOf<TaskActionRecord?>(null) }
    val latest=remember(records) { records.groupBy { it.eventId to it.key }.mapValues { it.value.maxBy { row -> row.id }.id } }
    val visible=records.filter { EventTags.matches(it.title,it.label,emptyList(),search,emptySet()) && (filter=="全部" || it.action.label==filter) }
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            Text("完成记录",style=MaterialTheme.typography.headlineSmall)
            Text("显示最近${records.size}条操作；完成${records.count { it.action==TaskActionType.DONE }}次，撤销${records.count { it.action==TaskActionType.REOPEN }}次。停止提示与确认收到不计为完成。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedTextField(search,{search=it.take(500)},label={Text("搜索事项或发生日期")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("全部","完成本次","撤销完成").forEach { value -> FilterChip(filter==value,{filter=value},label={Text(value)}) }
            }
            TextButton(enabled=!busy,onClick=onExport) { Text("导出最近完成操作CSV（最多10000条）") }
        }
        if(visible.isEmpty()) item { Text(if(records.isEmpty()) "还没有完成记录。编辑事项，开启“记录待办完成”，再从提醒页选择具体发生。" else "没有匹配的操作，请调整搜索或筛选。") }
        items(visible,key={it.id}) { record ->
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(record.title,style=MaterialTheme.typography.titleLarge)
                Text(record.label,style=MaterialTheme.typography.bodyMedium)
                Text("${record.action.label} · "+Instant.ofEpochMilli(record.atEpochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(record.action==TaskActionType.DONE && latest[record.eventId to record.key]==record.id) {
                    if(record.eventId in normalEventIds) TextButton(enabled=!busy,onClick={undo=record}) { Text("撤销这次完成") }
                    else Text("事项在回收站或已移除，完成记录保留。",style=MaterialTheme.typography.bodySmall)
                } else if(latest[record.eventId to record.key]!=record.id) Text("已有后续操作",style=MaterialTheme.typography.labelMedium)
                HorizontalDivider()
            }
        }
    }
    undo?.let { record -> AlertDialog(onDismissRequest={undo=null},title={Text("撤销这次完成？")},text={
        Text("${record.title}\n${record.label}\n\n仅按当前配置恢复此发生尚未触发的未来提醒。过去时刻、旧稍后、已开始的随机窗口不补响；停用事项保持停用。当前规则若不再覆盖这次，则不会新增提醒。撤销操作也会保留记录。")
    },confirmButton={TextButton(enabled=!busy,onClick={undo=null;onReopen(record.id)}) { Text("确认撤销完成") }},dismissButton={TextButton(onClick={undo=null}) { Text("取消") }}) }
}
