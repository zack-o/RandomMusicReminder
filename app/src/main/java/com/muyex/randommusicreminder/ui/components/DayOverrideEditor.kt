package com.muyex.randommusicreminder.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.muyex.randommusicreminder.domain.planner.*
import com.muyex.randommusicreminder.domain.time.ReminderClock
import com.muyex.randommusicreminder.ui.UiScheduleType
import java.time.LocalDate

@Composable
fun DayOverrideEditor(type:UiScheduleType,anchor:LocalDate,end:LocalDate?,cycle:ShiftCycle,values:List<DayOverride>,onChange:(List<DayOverride>)->Unit) {
    val context=LocalContext.current
    var dateText by remember { mutableStateOf(maxOf(anchor,LocalDate.now()).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var replacing by remember { mutableStateOf<Pair<LocalDate,ShiftDay>?>(null) }
    var pending by remember { mutableStateOf<DayOverride?>(null) }
    var restoring by remember { mutableStateOf<DayOverride?>(null) }
    var ranges by remember { mutableStateOf(false) }
    fun date():LocalDate? {
        val date=runCatching { LocalDate.parse(dateText) }.getOrNull()
        error=when {
            date==null || date.year !in 1900..2200 -> "请填写有效的yyyy-MM-dd日期"
            date<anchor || (end!=null && date>end) -> "新增调整须在当前开始和结束日期范围内"
            values.size>=DayOverrides.MAX_DATES && values.none { it.date==date } -> "单日调整最多100个日期，请先恢复不再需要的调整"
            else -> null
        }
        return date.takeIf { error==null }
    }
    fun original(date:LocalDate)=if(type==UiScheduleType.SHIFT) cycle.dayOn(anchor,date)?.let(::describeOverrideDay) ?: "不在原周期范围内" else "当前事项的原重复规则"
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        HorizontalDivider()
        Text("日期临时调整",style=MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick={ranges=true},modifier=Modifier.fillMaxWidth()) { Text("连续日期暂停／恢复") }
        Text("仅改这件事选定的日期，后续周期不移动。轮班请选择班次开始日，次日时刻与跨日提前点随该日一起处理。",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(dateText,{dateText=it.take(10);error=null},label={Text("调整日期（yyyy-MM-dd）")},singleLine=true,modifier=Modifier.fillMaxWidth())
        TextButton(onClick={
            val selected=runCatching { LocalDate.parse(dateText) }.getOrDefault(LocalDate.now())
            DatePickerDialog(context,{_,year,month,day->dateText=LocalDate.of(year,month+1,day).toString();error=null},selected.year,selected.monthValue-1,selected.dayOfMonth).show()
        }) { Text("用日历选择日期") }
        runCatching { LocalDate.parse(dateText) }.getOrNull()?.let { selected->Text("原规则：${original(selected)}",style=MaterialTheme.typography.bodySmall) }
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick={date()?.let { pending=DayOverride(it,DayOverrideKind.SKIP) }}) { Text("跳过这一天") }
            if(type==UiScheduleType.SHIFT) OutlinedButton(onClick={date()?.let { selected ->
                replacing=selected to (values.firstOrNull { it.date==selected }?.day ?: cycle.dayOn(anchor,selected)
                    ?: ShiftDay("临时班次",times=listOf(ShiftTime(java.time.LocalTime.of(8,30)))))
            }}) { Text("替换当天时刻") }
        }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        Text("已设${values.size}个日期。日历过滤仍生效；当前正在提示的会话请手动停止。这里先修改草稿，保存事项后才生效。",style=MaterialTheme.typography.bodySmall)
        if(values.isNotEmpty()) Column(Modifier.heightIn(max=300.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            values.sortedBy { it.date }.forEach { value ->
                Column {
                    Text("${value.date} · "+if(value.kind==DayOverrideKind.SKIP) "跳过" else "替换：${value.day?.label}",style=MaterialTheme.typography.titleMedium)
                    Text("原规则：${original(value.date)}",style=MaterialTheme.typography.bodySmall)
                    value.day?.let { Text("本次：${describeOverrideDay(it)}",style=MaterialTheme.typography.bodyMedium) }
                    if(value.date<anchor || (end!=null && value.date>end)) Text("已不在当前日期范围内，仅保留配置",style=MaterialTheme.typography.bodySmall)
                    Row {
                        if(type==UiScheduleType.SHIFT && value.kind==DayOverrideKind.REPLACE_SHIFT) TextButton(onClick={replacing=value.date to checkNotNull(value.day)}) { Text("编辑本次") }
                        TextButton(onClick={restoring=value}) { Text("恢复原规则") }
                    }
                }
            }
        }
    }
    if(ranges) DayOverrideRangeDialog(RangeAdjustmentScope(com.muyex.randommusicreminder.domain.model.ScheduleType.valueOf(type.name),anchor,end),
        runCatching { LocalDate.parse(dateText) }.getOrDefault(maxOf(anchor,LocalDate.now())),values,onChange) { ranges=false }
    replacing?.let { (date,day) -> ShiftDayDialog(0,day,{replacing=null},title="$date 的临时安排") { updated ->
        pending=DayOverride(date,DayOverrideKind.REPLACE_SHIFT,updated);replacing=null
    } }
    pending?.let { value -> AlertDialog(onDismissRequest={pending=null},title={Text("核对单日调整")},text={
        Text("${value.date}\n原规则：${original(value.date)}\n本次："+(value.day?.let(::describeOverrideDay) ?: "跳过此日期的所有提醒点和稍后")+
            "\n\n只影响这个事项日期，后续周期不移动。过去时刻不补响，已处理记录不改写。确认后先保留在草稿，保存事项才生效。")
    },confirmButton={TextButton(onClick={onChange((values.filterNot { it.date==value.date }+value).sortedBy { it.date });pending=null}) { Text("加入当前草稿") }},dismissButton={TextButton(onClick={pending=null}) { Text("返回修改") }}) }
    restoring?.let { value -> AlertDialog(onDismissRequest={restoring=null},title={Text("恢复这一天的原规则？")},text={
        Text("${value.date}\n将移除本次覆盖，按当前原周期与日历重新计算。只恢复未来时刻，不补响过去、旧稍后或已开始随机窗口，停用状态不变。确认后仍需保存事项。")
    },confirmButton={TextButton(onClick={onChange(values.filterNot { it.date==value.date });restoring=null}) { Text("恢复到当前草稿") }},dismissButton={TextButton(onClick={restoring=null}) { Text("取消") }}) }
}

internal fun describeOverrideDay(day:ShiftDay)=day.label+if(!day.enabled) " · 关闭提醒" else " · "+day.times.joinToString("、") { (if(it.nextDay) "次日 " else "")+ReminderClock.format(it.time) }
