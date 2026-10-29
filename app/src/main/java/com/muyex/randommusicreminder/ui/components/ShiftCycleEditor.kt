package com.muyex.randommusicreminder.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.muyex.randommusicreminder.domain.planner.*
import com.muyex.randommusicreminder.domain.time.ReminderClock
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftCycleEditor(cycle: ShiftCycle, anchor: LocalDate, onChange: (ShiftCycle) -> Unit) {
    var length by remember(cycle.days.size) { mutableStateOf(cycle.days.size.toString()) }
    var editDay by remember { mutableStateOf<Int?>(null) }
    var shrink by remember { mutableStateOf<Int?>(null) }
    var template by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun resize(size: Int) {
        onChange(cycle.copy(days=if(size<=cycle.days.size) cycle.days.take(size) else cycle.days+List(size-cycle.days.size) { ShiftDay("休息",rest=true,enabled=false,times=emptyList()) }))
    }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("每个周期如何安排",style=MaterialTheme.typography.titleLarge)
        Text("开始日期是第1天。跨月跨年照常循环；休息日可以单独开启提醒。",style=MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("做二休一" to "白白休","白夜休休" to "白夜休休","白白夜夜休休" to "白白夜夜休休").forEach { (label,pattern) ->
                SuggestionChip(onClick={template=pattern},label={Text(label)})
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            OutlinedTextField(length,{length=it.take(2)},label={Text("周期天数（1～60）")},singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f))
            OutlinedButton(onClick={
                val size=length.toIntOrNull()
                if(size==null || size !in 1..60) error="周期须为1～60天"
                else { error=null;if(size<cycle.days.size) shrink=size else resize(size) }
            }) { Text("应用天数") }
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            cycle.days.forEachIndexed { index,day -> AssistChip(onClick={editDay=index},label={
                Text("第${index+1}天 ${day.label}${if(!day.enabled) " · 关闭" else " · ${day.times.size}次"}")
            }) }
        }
        OutlinedButton(onClick={preview=true},modifier=Modifier.fillMaxWidth()) { Text("预览两个完整周期") }
        Text("夜班名称不自动推测时间。下班若在次日，请编辑该时刻并勾选次日。假期过滤只跳过日期，不移动轮班周期。",style=MaterialTheme.typography.bodySmall)
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }
    editDay?.takeIf { it in cycle.days.indices }?.let { index -> ShiftDayDialog(index,cycle.days[index],{editDay=null}) { day ->
        onChange(cycle.copy(days=cycle.days.mapIndexed { i,item -> if(i==index) day else item }));editDay=null
    } }
    shrink?.let { size -> AlertDialog(onDismissRequest={shrink=null},title={Text("缩短到${size}天？")},text={Text("将从当前草稿中移除第${size+1}～${cycle.days.size}天的安排，保存事项后生效。")},
        confirmButton={TextButton(onClick={resize(size);shrink=null}){Text("确认缩短")}},dismissButton={TextButton(onClick={shrink=null}){Text("保留原周期")}}) }
    template?.let { pattern -> AlertDialog(onDismissRequest={template=null},title={Text("使用轮班模板？")},text={Text("替换当前草稿的${cycle.days.size}天安排为${pattern.length}天模板；其他事项不会改变，保存后生效。")},
        confirmButton={TextButton(onClick={onChange(ShiftCycle.template(pattern));template=null}){Text("替换草稿")}},dismissButton={TextButton(onClick={template=null}){Text("取消")}}) }
    if(preview) AlertDialog(onDismissRequest={preview=false},title={Text("原始轮班周期预览")},text={
        val first=maxOf(anchor,LocalDate.now())
        LazyColumn(Modifier.heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { Text("以开始日${anchor}为第1天。此处展示原周期；含日历过滤与提前点的安排，请用编辑器底部“预览未来安排”核对。",style=MaterialTheme.typography.bodySmall) }
            items((0 until cycle.days.size*2).map { first.plusDays(it.toLong()) }) { date ->
                val index=cycle.dayIndex(anchor,date)!!;val day=cycle.days[index]
                Text("$date · 第${index+1}天 ${day.label}\n"+if(!day.enabled) "不提醒（此周期日已关闭）" else day.times.joinToString("、") { (if(it.nextDay) "次日 " else "")+ReminderClock.format(it.time) })
            }
        }
    },confirmButton={TextButton(onClick={preview=false}){Text("关闭预览")}})
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShiftDayDialog(index:Int,initial:ShiftDay,onDismiss:()->Unit,title:String?=null,onSave:(ShiftDay)->Unit) {
    var day by remember { mutableStateOf(initial) }
    var editingSlot by remember { mutableStateOf<ShiftTime?>(null) }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest=onDismiss,title={Text(title ?: "第${index+1}天的安排")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(day.label,{day=day.copy(label=it.take(30))},label={Text("班次名称")},singleLine=true)
            Row(verticalAlignment=Alignment.CenterVertically) { Text("休息日",Modifier.weight(1f));Switch(day.rest,{day=day.copy(rest=it)}) }
            Row(verticalAlignment=Alignment.CenterVertically) { Text("当天启用提醒",Modifier.weight(1f));Switch(day.enabled,{day=day.copy(enabled=it)}) }
            Text("关闭提醒会保留这些时间，休息日也可以单独开启。",style=MaterialTheme.typography.bodySmall)
            day.times.forEach { slot ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={editingSlot=slot},modifier=Modifier.weight(1f)) { Text((if(slot.nextDay) "次日 " else "")+ReminderClock.format(slot.time)) }
                    TextButton(onClick={day=day.copy(times=day.times.filterNot { it.key==slot.key })}) { Text("移除") }
                }
            }
            OutlinedButton(enabled=day.times.size<8,onClick={adding=true}) { Text("添加提醒时刻") }
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }
    },confirmButton={TextButton(onClick={
        error=when {
            day.label.isBlank()->"请填写班次名称"
            day.enabled && day.times.isEmpty()->"启用时须至少设置一个时刻"
            day.times.map { it.time to it.nextDay }.distinct().size!=day.times.size->"同一轮班日的时刻不能重复"
            else->null
        }
        if(error==null) onSave(day.copy(label=day.label.trim()))
    }){Text("保存这一天")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
    if(adding || editingSlot!=null) {
        val initialSlot=remember(editingSlot?.key,adding) { editingSlot ?: ShiftTime(LocalTime.of(9,0)) }
        ShiftTimeDialog(initialSlot,onDismiss={adding=false;editingSlot=null}) { slot ->
            day=day.copy(times=if(adding) day.times+slot else day.times.map { if(it.key==slot.key) slot else it })
            adding=false;editingSlot=null
        }
    }
}

@Composable
private fun ShiftTimeDialog(initial:ShiftTime,onDismiss:()->Unit,onSave:(ShiftTime)->Unit) {
    var slot by remember(initial.key) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("轮班提醒时刻")},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            TimeWheelPicker(slot.time,true) { slot=slot.copy(time=it) }
            Row(verticalAlignment=Alignment.CenterVertically) { Text("在次日提醒",Modifier.weight(1f));Switch(slot.nextDay,{slot=slot.copy(nextDay=it)}) }
            Text("次日时刻仍属于这一轮班日，例如夜班次日08:00下班。",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(onClick={onSave(slot)}){Text("保存时刻")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}
