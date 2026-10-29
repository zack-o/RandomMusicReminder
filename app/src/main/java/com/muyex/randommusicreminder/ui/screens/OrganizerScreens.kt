package com.muyex.randommusicreminder.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muyex.randommusicreminder.domain.organizer.EventTags
import com.muyex.randommusicreminder.ui.ReminderListItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class TagUsage(val name: String, val normal: Int, val deleted: Int)
private data class TrashConfirmation(val action: String, val items: List<ReminderListItem>)
private fun organizerTime(value: Long?) = value?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }.orEmpty()

@Composable
fun TagsScreen(normal: List<ReminderListItem>, deleted: List<ReminderListItem>, busy: Boolean,
    onRename: (String,String)->Unit, onRemove: (String)->Unit, modifier: Modifier=Modifier) {
    val usages=remember(normal,deleted) {
        (normal+deleted).flatMap { it.tags }.distinctBy(EventTags::key).sorted().map { tag ->
            TagUsage(tag,normal.count { row -> row.tags.any { EventTags.key(it)==EventTags.key(tag) } },
                deleted.count { row -> row.tags.any { EventTags.key(it)==EventTags.key(tag) } })
        }
    }
    var edit by remember { mutableStateOf<TagUsage?>(null) }
    var remove by remember { mutableStateOf<TagUsage?>(null) }
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("标签管理",style=MaterialTheme.typography.headlineSmall)
            Text("在事项中添加标签；这里可统一改名或移除分类。回收站中的标签也会同步更新。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(usages.isEmpty()) item { Text("还没有标签。编辑事项，在“基本”页添加工作、生活或自己的分类。",Modifier.padding(vertical=24.dp)) }
        items(usages,key={EventTags.key(it.name)}) { tag ->
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(tag.name,style=MaterialTheme.typography.titleLarge)
                Text("正常事项${tag.normal}项，回收站${tag.deleted}项",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick={edit=tag},enabled=!busy) { Text("改名") }
                    TextButton(onClick={remove=tag},enabled=!busy) { Text("移除标签") }
                }
                HorizontalDivider()
            }
        }
    }
    edit?.let { tag ->
        var name by remember(tag.name) { mutableStateOf(tag.name) }
        var error by remember(tag.name) { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest={edit=null},title={Text("修改标签名称")},text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("将更新正常事项${tag.normal}项、回收站${tag.deleted}项。已有同名标签会合并；提醒时间和启用状态保留。")
                OutlinedTextField(name,{name=it.take(80);error=null},label={Text("标签名称")},singleLine=true)
                error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            }
        },confirmButton={TextButton(enabled=!busy,onClick={
            runCatching { EventTags.normalize(listOf(name)).single() }.onSuccess { onRename(tag.name,it);edit=null }.onFailure { error=it.message }
        }) { Text("确认改名") }},dismissButton={TextButton(onClick={edit=null}) { Text("取消") }})
    }
    remove?.let { tag -> AlertDialog(onDismissRequest={remove=null},title={Text("移除“${tag.name}”标签？")},text={
        Text("将从正常事项${tag.normal}项、回收站${tag.deleted}项移除这个标签。事项、详细内容与提醒安排都会保留。")
    },confirmButton={TextButton(enabled=!busy,onClick={onRemove(tag.name);remove=null}) { Text("确认移除标签") }},dismissButton={TextButton(onClick={remove=null}) { Text("取消") }}) }
}

@Composable
fun TrashScreen(events: List<ReminderListItem>, busy: Boolean, onRestore: (Long)->Unit,
    onDelete: (List<Long>)->Unit, modifier: Modifier=Modifier) {
    var search by rememberSaveable { mutableStateOf("") }
    var detailsId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmation by remember { mutableStateOf<TrashConfirmation?>(null) }
    val visible=events.filter { EventTags.matches(it.title,it.note,it.tags,search,emptySet()) }
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            Text("回收站",style=MaterialTheme.typography.headlineSmall)
            Text("共${events.size}项，手动清理。恢复后默认停用，核对配置后再启用。响铃历史独立保留。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(events.isNotEmpty()) item {
            OutlinedTextField(search,{search=it.take(500)},label={Text("搜索回收事项")},singleLine=true,modifier=Modifier.fillMaxWidth())
            TextButton(enabled=!busy,colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error),onClick={confirmation=TrashConfirmation("clear",events.toList())}) { Text("清空回收站（全部${events.size}项）") }
        }
        if(visible.isEmpty()) item { Text(if(events.isEmpty()) "回收站为空。移除的事项会先保存在这里。" else "没有匹配的事项，请调整搜索内容。",Modifier.padding(vertical=24.dp)) }
        items(visible,key={it.id}) { event ->
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Column(Modifier.fillMaxWidth().clickable { detailsId=event.id }.padding(vertical=8.dp)) {
                    Text(event.title,style=MaterialTheme.typography.titleLarge)
                    Text(event.scheduleText,style=MaterialTheme.typography.bodyMedium)
                    if(event.tags.isNotEmpty()) Text(event.tags.joinToString("  ") { "#$it" },color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall)
                    Text("移入时间 ${organizerTime(event.deletedAtEpochMillis)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(enabled=!busy,onClick={confirmation=TrashConfirmation("restore",listOf(event))}) { Text("恢复") }
                    TextButton(enabled=!busy,colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error),onClick={confirmation=TrashConfirmation("delete",listOf(event))}) { Text("永久删除") }
                }
                HorizontalDivider()
            }
        }
    }
    events.firstOrNull { it.id==detailsId }?.let { event -> AlertDialog(onDismissRequest={detailsId=null},title={Text(event.title)},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(event.scheduleText);Text(event.timeText);Text(event.tags.joinToString("、").ifEmpty { "未分类" })
            Text(event.note.ifBlank { "未填写详细事项" })
            Text("创建 ${organizerTime(event.createdAtEpochMillis)}\n移入 ${organizerTime(event.deletedAtEpochMillis)}",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(enabled=!busy,onClick={detailsId=null;confirmation=TrashConfirmation("restore",listOf(event))}) { Text("恢复") }},dismissButton={TextButton(onClick={detailsId=null}) { Text("关闭") }}) }
    confirmation?.let { pending ->
        val restore=pending.action=="restore"
        AlertDialog(onDismissRequest={confirmation=null},title={Text(if(restore) "恢复这个事项？" else if(pending.action=="clear") "清空这${pending.items.size}项？" else "永久删除这个事项？")},text={
            Text(pending.items.take(3).joinToString("\n") { it.title }+(if(pending.items.size>3) "\n等${pending.items.size}项" else "")+"\n\n"+
                if(restore) "恢复后保持停用，原创建时间与配置保留。恢复前时刻、旧稍后及恢复时已开始的随机窗口不会重新安排，请到提醒页核对后启用。"
                else "永久移除这些回收事项及其配置，无法在应用内恢复；响铃历史保留。随后新移入的事项不在本次范围。")
        },confirmButton={TextButton(enabled=!busy,colors=ButtonDefaults.textButtonColors(contentColor=if(restore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),onClick={
            confirmation=null
            if(restore) onRestore(pending.items.single().id) else onDelete(pending.items.map { it.id })
        }) { Text(if(restore) "恢复并停用" else "确认永久删除") }},dismissButton={TextButton(onClick={confirmation=null}) { Text("取消") }})
    }
}
