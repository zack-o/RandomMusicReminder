package com.muyex.randommusicreminder.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muyex.randommusicreminder.domain.planner.SchedulePreview
import com.muyex.randommusicreminder.ui.EventDraftUi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulePreviewDialog(draft:EventDraftUi,load:suspend (EventDraftUi)->SchedulePreview,onDismiss:()->Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    var result by remember(draft) { mutableStateOf<SchedulePreview?>(null) }
    var failure by remember(draft) { mutableStateOf<String?>(null) }
    LaunchedEffect(draft,refresh) {
        result=null;failure=null
        try { result=load(draft) }
        catch(cancelled:CancellationException) { throw cancelled }
        catch(error:Exception) { failure=error.message ?: "计算失败，请返回检查草稿后重试" }
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding()) {
                TopAppBar(title={Text("未来安排预览")},navigationIcon={IconButton(onClick=onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回编辑") }},
                    actions={TextButton(onClick={refresh++}) { Text("刷新") }})
                val data=result
                if(data==null) Column(Modifier.weight(1f).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text(draft.title,style=MaterialTheme.typography.headlineSmall)
                    if(failure==null) { CircularProgressIndicator();Text("正在核对草稿、日历与已处理记录…") }
                    else { Text(checkNotNull(failure),color=MaterialTheme.colorScheme.error);OutlinedButton(onClick={refresh++}) { Text("重新计算") } }
                } else SchedulePreviewContent(data,draft.title,Modifier.weight(1f))
                HorizontalDivider()
                Button(onClick=onDismiss,modifier=Modifier.fillMaxWidth().padding(16.dp)) { Text("返回编辑并核对保存") }
            }
        }
    }
}

@Composable
internal fun SchedulePreviewContent(data:SchedulePreview,title:String,modifier:Modifier=Modifier) {
    val zone=ZoneId.of(data.zone)
    val full=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val day=DateTimeFormatter.ofPattern("MM-dd EEEE")
    val time=DateTimeFormatter.ofPattern("HH:mm:ss")
    fun instant(value:Long)=Instant.ofEpochMilli(value).atZone(zone)
    LazyColumn(modifier.fillMaxWidth().testTag("schedule_preview_list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {
            Text(title,style=MaterialTheme.typography.headlineSmall)
            Text("未来90天 · 最多14条安排",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            Text("${instant(data.generatedAt).format(full)} 至 ${instant(data.untilExclusive).format(full)}（不含结束时刻）\n时区：${data.zone}",style=MaterialTheme.typography.bodySmall)
        }
        items(data.warnings) { Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        if(data.items.isEmpty()) item {
            Text(if(data.paused) "停用中，当前没有将安排的提示" else "此范围没有未来提示",style=MaterialTheme.typography.titleLarge)
            if(!data.paused) Text("请核对开始/结束日期、重复规则、日历过滤和已处理记录。90天之外的安排未列出。")
        }
        items(data.items,key={it.id}) { row ->
            Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(instant(row.atEpochMillis).format(day),style=MaterialTheme.typography.titleMedium)
                Text(instant(row.atEpochMillis).format(time)+(row.endEpochMillis?.let { " ～ "+(if(instant(it).toLocalDate()!=instant(row.atEpochMillis).toLocalDate()) "${instant(it).toLocalDate()} " else "")+instant(it).format(time) } ?: ""),
                    style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
                Text(row.label,style=MaterialTheme.typography.titleSmall)
                Text(row.reason,style=MaterialTheme.typography.bodyMedium)
                HorizontalDivider(Modifier.padding(top=10.dp))
            }
        }
        if(data.hasMore) item { Text("范围内还有更多安排，此处按时间显示前14条；多个提前点分别计数，随机主提醒与补充合并展示。",style=MaterialTheme.typography.bodySmall) }
        if(data.omissions.isNotEmpty()) {
            item { Text("未安排原因（最多14条）",style=MaterialTheme.typography.titleLarge) }
            items(data.omissions) { row -> Text("${row.date}\n${row.reason}",style=MaterialTheme.typography.bodyMedium) }
        }
    }
}
