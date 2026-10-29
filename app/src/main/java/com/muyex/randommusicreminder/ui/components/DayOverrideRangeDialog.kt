package com.muyex.randommusicreminder.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muyex.randommusicreminder.domain.model.ScheduleType
import com.muyex.randommusicreminder.domain.planner.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
fun DayOverrideRangeDialog(scope:RangeAdjustmentScope,initialDate:LocalDate,values:List<DayOverride>,
    onApply:(List<DayOverride>)->Unit,onDismiss:()->Unit) {
    val first=if(scope.type==ScheduleType.ONCE) scope.start else initialDate
    val last=if(scope.type==ScheduleType.ONCE) first else scope.end?.takeIf { it>=first }?.let { minOf(first.plusDays(6),it) } ?: first.plusDays(6)
    var fromText by remember { mutableStateOf(first.toString()) }
    var throughText by remember { mutableStateOf(last.toString()) }
    var action by remember { mutableStateOf(RangeAdjustmentAction.SKIP) }
    var keepShifts by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<RangeAdjustmentPlan?>(null) }
    val keyboard=LocalSoftwareKeyboardController.current
    fun invalidate() { error=null;pending=null }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().imePadding()) {
                TopAppBar(title={Text("连续日期暂停／恢复")},navigationIcon={IconButton(onClick=onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回日期调整") }})
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text("为当前事项选择一段日期",style=MaterialTheme.typography.headlineSmall)
                    Text("起止当天都包含。按事项日期或班次开始日计算，跨日提前与次日提醒一起处理，后续周期不移动。",style=MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilterChip(action==RangeAdjustmentAction.SKIP,{action=RangeAdjustmentAction.SKIP;invalidate()},label={Text("暂停这段日期")})
                        FilterChip(action==RangeAdjustmentAction.RESTORE_SKIPS,{action=RangeAdjustmentAction.RESTORE_SKIPS;invalidate()},label={Text("恢复区间内跳过")})
                    }
                    RangeDateField("开始日期",fromText) { fromText=it;invalidate() }
                    RangeDateField("结束日期",throughText) { throughText=it;invalidate() }
                    if(action==RangeAdjustmentAction.SKIP) {
                        Text("已有临时班次如何处理",style=MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            FilterChip(keepShifts,{keepShifts=true;invalidate()},label={Text("保留已有临时班次")})
                            FilterChip(!keepShifts,{keepShifts=false;invalidate()},label={Text("一并替换为跳过")})
                        }
                        Text(if(keepShifts) "默认保留已有单日班次，这些日期仍按其配置处理。确认页会列出数量，其他日期加入跳过。" else
                            "区间内临时班次将被覆盖。以后恢复跳过只回到原周期，不会找回被覆盖的临时班次；确认前请核对清单。",style=MaterialTheme.typography.bodyMedium)
                        Text("一次最多100个日历日，合并后总调整日期也最多100个；超限整段不加入，不会只做一部分。",style=MaterialTheme.typography.bodySmall)
                    } else Text("只移除区间内已有的跳过设置。临时班次、区间外日期和停用状态保留；可以选择超出当前开始/结束范围的日期，清理旧跳过。",style=MaterialTheme.typography.bodyMedium)
                    Text("当前共有${values.size}个日期调整。按日历日操作，不代表每天原本都有提醒。这里只修改草稿，保存事项后才生效。",style=MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(bottom=8.dp)) }
                    Button(onClick={
                        keyboard?.hide()
                        try {
                            val from=runCatching { LocalDate.parse(fromText) }.getOrNull()
                            val through=runCatching { LocalDate.parse(throughText) }.getOrNull()
                            require(from!=null && through!=null) { "请填写有效的yyyy-MM-dd开始和结束日期" }
                            pending=DayOverrideRanges.preview(scope,values,from,through,action,keepShifts);error=null
                        } catch(problem:IllegalArgumentException) { error=problem.message ?: "区间无法应用，请检查日期和已有调整" }
                    },modifier=Modifier.fillMaxWidth()) { Text("核对区间操作") }
                    TextButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth()) { Text("返回日期调整") }
                }
            }
        }
    }
    pending?.let { plan -> AlertDialog(onDismissRequest={pending=null},title={Text("核对连续日期调整")},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("${plan.from} 至 ${plan.through}，含两端，共${plan.calendarDays}个日历日。",style=MaterialTheme.typography.titleMedium)
            if(plan.action==RangeAdjustmentAction.SKIP) Text("新增跳过${plan.addedSkips}天；原已跳过${plan.alreadySkipped}天；覆盖临时班次${plan.overwrittenShifts}天；保留临时班次${plan.keptShifts}天。")
            else Text("恢复已有跳过${plan.removedSkips}天，保留临时班次${plan.keptShifts}天。")
            Text("将改变${plan.changedDates.size}个日期，确认后共${plan.after.size}个日期调整。")
            val shifts=plan.before.filter { it.date in plan.from..plan.through && it.kind==DayOverrideKind.REPLACE_SHIFT }
            if(shifts.isNotEmpty()) {
                Text(if(plan.overwrittenShifts>0) "这些临时班次将改成跳过：" else "这些临时班次会保留：",style=MaterialTheme.typography.titleSmall)
                shifts.take(10).forEach { value -> Text("${value.date} · ${describeOverrideDay(checkNotNull(value.day))}",style=MaterialTheme.typography.bodySmall) }
                if(shifts.size>10) Text("另有${shifts.size-10}个临时班次，可返回单日列表逐项核对。",style=MaterialTheme.typography.bodySmall)
            }
            Text(if(plan.changedDates.isEmpty()) "此操作没有需要改变的日期，可返回调整范围。" else
                "这里只加入当前草稿，仍需保存事项。当前正在提示的会话不自动结束；暂停不算待办完成。恢复只按当前原规则安排未来，不补过去、旧稍后或已开始随机窗口。")
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }
    },confirmButton={TextButton(enabled=plan.changedDates.isNotEmpty(),onClick={
        try { val after=DayOverrideRanges.apply(plan,values,scope);onApply(after);pending=null;onDismiss() }
        catch(problem:IllegalArgumentException) { error=problem.message;pending=null }
    }) { Text("确认加入草稿") }},dismissButton={TextButton(onClick={pending=null;error=null}) { Text("返回修改") }}) }
}

@Composable
private fun RangeDateField(label:String,text:String,onChange:(String)->Unit) {
    val context=LocalContext.current
    OutlinedTextField(text,{onChange(it.take(10))},label={Text(label)},supportingText={Text("yyyy-MM-dd")},singleLine=true,modifier=Modifier.fillMaxWidth(),
        trailingIcon={IconButton(onClick={
            val date=runCatching { LocalDate.parse(text) }.getOrDefault(LocalDate.now())
            DatePickerDialog(context,{_,year,month,day->onChange(LocalDate.of(year,month+1,day).toString())},date.year,date.monthValue-1,date.dayOfMonth).show()
        }) { Icon(Icons.Outlined.CalendarMonth,"选择$label") }})
}
