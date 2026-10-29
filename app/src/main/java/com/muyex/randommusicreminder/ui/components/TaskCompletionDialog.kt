package com.muyex.randommusicreminder.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muyex.randommusicreminder.domain.tasks.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCompletionDialog(data:TaskChoiceList,busy:Boolean,onRange:(TaskChoiceRange)->Unit,onComplete:(TaskChoice)->Unit,onDismiss:()->Unit) {
    var selected by remember(data) { mutableStateOf(data.choices.firstOrNull()) }
    var confirming by remember { mutableStateOf<TaskChoice?>(null) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("选择要完成的这一次")},text={
        Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(data.title,style=MaterialTheme.typography.titleMedium)
            Text("每次只完成一个发生；同一天不同轮班时刻、其他事项和下一周期继续保留。",style=MaterialTheme.typography.bodySmall)
            if(data.eventId>0) FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { TaskChoiceRange.entries.forEach { range ->
                FilterChip(data.range==range,{onRange(range)},enabled=!busy,label={Text(range.label)})
            } }
            if(data.choices.isEmpty()) Text("此范围没有可完成的发生。跟踪面向已排程事项；停用期间不新增，已完成项可从完成记录查看。")
            data.choices.forEach { choice ->
                Row(Modifier.fillMaxWidth().clickable(enabled=!busy) { selected=choice }.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected==choice,{selected=choice},enabled=!busy)
                    Column(Modifier.weight(1f)) {
                        if(data.eventId==0L) Text(choice.title,style=MaterialTheme.typography.titleMedium)
                        Text(choice.label)
                        Text((if(choice.ringing) "本次正在提示；" else "")+"待触发${choice.pendingCount}个提醒",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if(data.eventId>0) Text("每个范围最多显示60次；逾期发生会保留，不随旧响铃记录清理。",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(enabled=!busy && selected!=null,onClick={confirming=selected}) { Text("核对完成范围") }},dismissButton={TextButton(onClick=onDismiss) { Text("取消") }})
    confirming?.let { choice -> AlertDialog(onDismissRequest={confirming=null},title={Text("完成这一次事项？")},text={
        Text("${choice.title}\n${choice.label}\n\n将记录本次完成，取消这次发生的后续提醒点和稍后提醒（当前待触发${choice.pendingCount}个）。${if(choice.ringing) "本次正在提示的部分会结束；" else ""}其他时刻、同刻其他事项与未来周期保留。")
    },confirmButton={TextButton(enabled=!busy,onClick={confirming=null;onComplete(choice)}) { Text("确认完成本次") }},dismissButton={TextButton(onClick={confirming=null}) { Text("返回核对") }}) }
}
