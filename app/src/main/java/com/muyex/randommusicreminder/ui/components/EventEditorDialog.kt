package com.muyex.randommusicreminder.ui.components

import android.app.DatePickerDialog
import com.muyex.randommusicreminder.domain.time.ReminderClock
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import com.muyex.randommusicreminder.domain.model.TimingMode
import com.muyex.randommusicreminder.domain.planner.RandomWindow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muyex.randommusicreminder.domain.calendar.HolidayPolicy
import com.muyex.randommusicreminder.domain.model.MissingDayPolicy
import com.muyex.randommusicreminder.ui.*
import java.time.*
import java.time.format.DateTimeFormatter

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventEditorDialog(initial: EventDraftUi, tracks: List<MusicListItem>, gentleDefaultSeconds: Int, strongDefaultSeconds: Int,
    onDraftChange: (EventDraftUi) -> Unit, onDismiss: () -> Unit, onSave: (EventDraftUi) -> Unit, onDelete: ((Long) -> Unit)?,
    onPreview:(suspend (EventDraftUi)->com.muyex.randommusicreminder.domain.planner.SchedulePreview)?=null) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    val pager = rememberPagerState(pageCount = { 4 })
    val page = pager.currentPage
    val scope = rememberCoroutineScope()
    fun goTo(value: Int) { scope.launch { pager.animateScrollToPage(value) } }
    var windowEndText by remember(initial.id) { mutableStateOf(initial.windowEndTime.format(clockFormat)) }
    var includeRemaining by remember(initial.id) { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<EventDraftUi?>(null) }
    var pendingShiftSave by remember { mutableStateOf<EventDraftUi?>(null) }
    var previewDraft by remember { mutableStateOf<EventDraftUi?>(null) }
    var dateText by remember(initial.id) { mutableStateOf(initial.onceDate.toString()) }
    var timeText by remember(initial.id) { mutableStateOf(ReminderClock.format(initial.eventTime)) }
    var directlyInputTime by remember { mutableStateOf(false) }
    var intervalText by remember(initial.id) { mutableStateOf(initial.intervalDays.toString()) }
    var hasEnd by remember(initial.id) { mutableStateOf(initial.endDate != null) }
    var endText by remember(initial.id) { mutableStateOf((initial.endDate ?: initial.onceDate.plusYears(1)).toString()) }
    var pointIndex by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteConfirmation by remember { mutableStateOf(false) }
    var musicSearch by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val pageScrolls = List(4) { rememberScrollState() }
    LaunchedEffect(draft) { onDraftChange(draft) }

    fun chooseDate(value: String, update: (String) -> Unit) {
        val current = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
        DatePickerDialog(context, { _, year, month, day -> update(LocalDate.of(year, month + 1, day).toString()) },
            current.year, current.monthValue - 1, current.dayOfMonth).show()
    }

    fun prepareDraft(preview:Boolean) {
        val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
        val time = if(draft.scheduleType == UiScheduleType.SHIFT) draft.eventTime else ReminderClock.parse(timeText,draft.timingMode == TimingMode.FIXED)
        val end = if (hasEnd && draft.scheduleType != UiScheduleType.ONCE) runCatching { LocalDate.parse(endText) }.getOrNull() else null
        val interval = intervalText.toIntOrNull()
        val windowEnd = runCatching { LocalTime.parse(windowEndText) }.getOrNull()?.takeIf { Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(windowEndText) }
        val validWindow = time != null && windowEnd != null && runCatching { RandomWindow.endMinute(time, windowEnd, draft.windowCrossesMidnight) }.isSuccess
        val usable = tracks.filter { it.enabled && it.available }.map { it.id }.toSet()
        error = when {
            draft.title.isBlank() -> { goTo(0); "请填写事项名称" }
            time == null -> { goTo(0); "请填写有效的HH:mm或HH:mm:ss；随机窗口只使用整分钟" }
            date == null || date.year !in 1900..2200 -> { goTo(1); "日期须为1900～2200年的yyyy-MM-dd" }
            draft.scheduleType == UiScheduleType.INTERVAL && (interval == null || interval !in 1..3650) -> { goTo(1); "间隔须为1～3650天" }
            hasEnd && draft.scheduleType != UiScheduleType.ONCE && (end == null || end < date || end.year !in 1900..2200) -> { goTo(1); "结束日期无效或早于开始日期" }
            draft.scheduleType == UiScheduleType.WEEKLY && draft.weeklyDays.isEmpty() -> { goTo(1); "每周至少选择一天" }
            draft.scheduleType != UiScheduleType.SHIFT && draft.dayOverrides.any { it.kind==com.muyex.randommusicreminder.domain.planner.DayOverrideKind.REPLACE_SHIFT } -> { goTo(1); "请先恢复单日轮班替换，再保存其他重复类型" }
            draft.scheduleType == UiScheduleType.SHIFT && runCatching { draft.shiftCycle.validate() }.isFailure -> { goTo(1); runCatching { draft.shiftCycle.validate() }.exceptionOrNull()?.message ?: "轮班配置无效" }
            draft.reminderPoints.isEmpty() -> { goTo(2); "至少保留一个提醒点" }
            draft.timingMode == TimingMode.RANDOM_WINDOW && !validWindow -> { goTo(0); "窗口结束须晚于开始、跨度少于24小时，跨午夜请勾选次日" }
            draft.timingMode == TimingMode.FIXED && draft.scheduleType == UiScheduleType.ONCE && draft.reminderPoints.any { !LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).plusMinutes(it.offsetMinutes.toLong()).toInstant().isAfter(Instant.now()) } -> { goTo(2); "单次事件有已过期的提醒点，请调整日期或提前量" }
            draft.outputMode.audio && draft.musicPoolMode == UiMusicPoolMode.CUSTOM && draft.customTrackIds.intersect(usable).isEmpty() -> { goTo(3); "请选择至少一首可用音乐" }
            else -> null
        }
        if (error != null) return
        var candidate = draft.copy(title = draft.title.trim(), onceDate = checkNotNull(date), eventTime = checkNotNull(time),
            intervalDays = if (draft.scheduleType == UiScheduleType.INTERVAL) checkNotNull(interval) else draft.intervalDays.coerceIn(1, 3650),
            endDate = end, customTrackIds = draft.customTrackIds.intersect(usable), reminderPoints = draft.reminderPoints.sortedBy { it.offsetMinutes }, windowEndTime = windowEnd ?: draft.windowEndTime)
        if(candidate.scheduleType == UiScheduleType.SHIFT) { if(preview) previewDraft=candidate else pendingShiftSave=candidate }
        else if (candidate.timingMode == TimingMode.RANDOM_WINDOW) {
            val now = Instant.now(); val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
            val unchanged = initial.id != null && initial.timingMode == candidate.timingMode && initial.eventTime == candidate.eventTime && initial.windowEndTime == candidate.windowEndTime && initial.windowCrossesMidnight == candidate.windowCrossesMidnight && initial.onceDate == candidate.onceDate && initial.scheduleType == candidate.scheduleType && initial.weeklyDays == candidate.weeklyDays
            val currentWindow = listOf(today.minusDays(1), today).map { date -> RandomWindow.candidates(date, candidate.eventTime, candidate.windowEndTime, candidate.windowCrossesMidnight, zone) }
                .firstOrNull { list -> list.isNotEmpty() && now >= list.first().instant && now <= list.last().instant }
            val notBefore = if (unchanged) initial.windowFirstNotBeforeEpochMillis else if (!includeRemaining && currentWindow != null) currentWindow.last().instant.toEpochMilli() else now.toEpochMilli()
            candidate = candidate.copy(windowFirstNotBeforeEpochMillis = notBefore)
            if (candidate.scheduleType == UiScheduleType.ONCE && RandomWindow.candidates(candidate.onceDate, candidate.eventTime, candidate.windowEndTime, candidate.windowCrossesMidnight, zone).none { it.instant.toEpochMilli() > (notBefore ?: now.toEpochMilli()) && it.instant > now }) {
                error = "单次时间窗已结束或已跳过；可选择剩余时间或调整日期"; goTo(0)
            } else if(preview) previewDraft=candidate else pendingSave = candidate
        } else if(preview) previewDraft=candidate else onSave(candidate)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().imePadding()) {
                TopAppBar(title = { Text(if (draft.id == null) "新建提醒" else "编辑提醒") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "关闭编辑") } },
                    actions = { if (draft.id != null && onDelete != null) IconButton(onClick = { deleteConfirmation = true }) { Icon(Icons.Outlined.DeleteOutline, "移入回收站") } })
                SecondaryTabRow(selectedTabIndex = page) {
                    listOf("基本", "重复", if (draft.timingMode == TimingMode.RANDOM_WINDOW) "响铃" else "提醒点", "音乐").forEachIndexed { index, label ->
                        Tab(selected = page == index, onClick = { goTo(index); error = null }, text = { Text(label) })
                    }
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth().testTag("editor_pager"), beyondViewportPageCount = 0) { editorPage ->
                Column(Modifier.fillMaxSize().verticalScroll(pageScrolls[editorPage]).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (editorPage) {
                        0 -> {
                            EditorHeading("安排一件事", "选好时间，再补充事项与重复规则")
                            OutlinedTextField(draft.title, { draft = draft.copy(title = it.take(500)) }, label = { Text("事项名称") }, placeholder = { Text("例如：项目周会") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(draft.timingMode == TimingMode.FIXED, { draft = draft.copy(timingMode = TimingMode.FIXED) }, label = { Text("固定时间") })
                                FilterChip(draft.timingMode == TimingMode.RANDOM_WINDOW, { draft = draft.copy(timingMode = TimingMode.RANDOM_WINDOW, eventTime = draft.eventTime.withSecond(0).withNano(0)); timeText = draft.eventTime.format(clockFormat) }, enabled = draft.scheduleType != UiScheduleType.SHIFT, label = { Text("时间窗随机") })
                            }
                            if(draft.scheduleType == UiScheduleType.SHIFT) {
                                Text("轮班使用每天独立的时刻；共${draft.shiftCycle.days.size}天一个周期。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
                                OutlinedButton(onClick={goTo(1)}) { Text("编辑轮班安排") }
                            } else {
                            TimeWheelPicker(draft.eventTime, draft.timingMode == TimingMode.FIXED) { value ->
                                draft = draft.copy(eventTime = value); timeText = ReminderClock.format(value)
                            }
                            TextButton(onClick = { directlyInputTime = !directlyInputTime }) { Text(if (directlyInputTime) "收起直接输入" else "直接输入时间") }
                            if (directlyInputTime) OutlinedTextField(timeText, { input -> timeText = input; ReminderClock.parse(input,draft.timingMode == TimingMode.FIXED)?.let { value -> draft = draft.copy(eventTime = value) } },
                                label = { Text(if (draft.timingMode == TimingMode.FIXED) "HH:mm 或 HH:mm:ss" else "窗口开始 HH:mm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            }
                            if (draft.timingMode == TimingMode.RANDOM_WINDOW) {
                                OutlinedTextField(windowEndText, { windowEndText = it; runCatching { LocalTime.parse(it) }.getOrNull()?.let { value -> draft = draft.copy(windowEndTime = value) } },
                                    label = { Text("窗口结束（HH:mm）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                    supportingText = { Text("随机窗口使用整分钟") })
                                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(draft.windowCrossesMidnight, { draft = draft.copy(windowCrossesMidnight = it) }); Text("结束在次日（跨午夜）") }
                                val minutes = runCatching { RandomWindow.endMinute(draft.eventTime, draft.windowEndTime, draft.windowCrossesMidnight) - draft.eventTime.hour * 60 - draft.eventTime.minute + 1 }.getOrNull()
                                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("在范围内随机响一次", style = MaterialTheme.typography.titleMedium)
                                        Text(minutes?.let { "包含开始和结束，可在 $it 个整分钟中随机响一次。查看或重启不会重选，连续两天可能相同。" } ?: "结束须晚于开始，跨度少于24小时。", style = MaterialTheme.typography.bodySmall)
                                        Text("适合有弹性的事项；抢票、开售等精确时点请使用固定时间。", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(includeRemaining, { includeRemaining = it }); Text("若已进入窗口，本次使用剩余时间", style = MaterialTheme.typography.bodyMedium) }
                                Text("默认跳过已开始的窗口；勾选后只从下一完整分钟到结束抽取。跨午夜按开始日重复。", style = MaterialTheme.typography.bodySmall)
                                Row(verticalAlignment = Alignment.CenterVertically) { Text("隐藏已选时刻", Modifier.weight(1f)); Switch(draft.hideWindowSelection, { draft = draft.copy(hideWindowSelection = it) }) }
                                Text("仅影响乐醒展示，系统下次闹钟仍可能显示实际时间。固定时间的提醒点会保留，切回后恢复。", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedTextField(draft.note, { draft = draft.copy(note = it.take(10_000)) }, label = { Text("详细事项（可选）") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
                            OutputOptions(draft.outputMode,draft.haptics) { mode,settings -> draft=draft.copy(outputMode=mode,haptics=settings) }
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("记录待办完成",style=MaterialTheme.typography.titleMedium);Text("可选择完成某一次；开启后跟踪今天起已排程的发生。停用期间不新增，停止提示不算完成。",style=MaterialTheme.typography.bodySmall) }
                                Switch(draft.trackCompletion,{draft=draft.copy(trackCompletion=it)})
                            }
                            EventTagsEditor(draft.tags) { draft=draft.copy(tags=it) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("启用提醒", style = MaterialTheme.typography.titleMedium); Text("停用只保留配置，不安排响铃", style = MaterialTheme.typography.bodySmall) }
                                Switch(draft.enabled, { draft = draft.copy(enabled = it) })
                            }
                            HorizontalDivider()
                            Text("规则摘要 · ${draft.outputMode.label}", style = MaterialTheme.typography.titleSmall)
                            Text("${draft.scheduleType.label} · ${if (draft.timingMode == TimingMode.RANDOM_WINDOW) "随机主提醒1次" else "${draft.reminderPoints.size} 个提醒点"} · ${draft.musicPoolMode.label}", color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { goTo(1) }) { Text("继续设置重复规则") }
                        }
                        1 -> {
                            EditorHeading("何时发生", "日期规则与节假日过滤分开设置")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { UiScheduleType.entries.forEach { type ->
                                FilterChip(draft.scheduleType == type, { draft = draft.copy(scheduleType = type, timingMode = if(type == UiScheduleType.SHIFT) TimingMode.FIXED else draft.timingMode) }, label = { Text(type.label) })
                            } }
                            OutlinedTextField(dateText, { dateText = it; runCatching { LocalDate.parse(it) }.getOrNull()?.let { date -> draft = draft.copy(onceDate = date) } },
                                label = { Text(if (draft.scheduleType == UiScheduleType.ONCE) "事件日期" else "开始日期（重复锚点）") }, supportingText = { Text(if(draft.scheduleType == UiScheduleType.SHIFT) "yyyy-MM-dd；此日期为轮班周期第1天" else "yyyy-MM-dd；月/年按此日期的日号/月日重复") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                trailingIcon = { IconButton(onClick = { chooseDate(dateText) { dateText = it; draft = draft.copy(onceDate = LocalDate.parse(it)) } }) { Icon(Icons.Outlined.CalendarMonth, "选择开始日期") } })
                            if(draft.scheduleType == UiScheduleType.SHIFT) ShiftCycleEditor(draft.shiftCycle,draft.onceDate) { draft=draft.copy(shiftCycle=it) }
                            if (draft.scheduleType == UiScheduleType.WEEKLY) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DayOfWeek.entries.forEach { day ->
                                    FilterChip(day in draft.weeklyDays, { draft = draft.copy(weeklyDays = if (day in draft.weeklyDays) draft.weeklyDays - day else draft.weeklyDays + day) }, label = { Text("周" + listOf("一", "二", "三", "四", "五", "六", "日")[day.value - 1]) })
                                }
                            }
                            if (draft.scheduleType == UiScheduleType.INTERVAL) {
                                OutlinedTextField(intervalText, { intervalText = it; it.toIntOrNull()?.let { days -> draft = draft.copy(intervalDays = days) } }, label = { Text("每隔多少天") }, suffix = { Text("天") },
                                    supportingText = { Text("1～3650个日历日，以开始日期为锚点，不按24小时累加") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                            }
                            if (draft.scheduleType in setOf(UiScheduleType.MONTHLY, UiScheduleType.YEARLY)) {
                                Text("不存在该日时", style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(draft.missingDayPolicy == MissingDayPolicy.SKIP, { draft = draft.copy(missingDayPolicy = MissingDayPolicy.SKIP) }, label = { Text("跳过当月/当年") })
                                    FilterChip(draft.missingDayPolicy == MissingDayPolicy.LAST_DAY, { draft = draft.copy(missingDayPolicy = MissingDayPolicy.LAST_DAY) }, label = { Text("取当月最后一天") })
                                }
                                Text("例如31日或2月29日。每次仍从原日期计算，不累积偏移；按公历，不支持农历。", style = MaterialTheme.typography.bodySmall)
                            }
                            if (draft.scheduleType != UiScheduleType.ONCE) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Text("设置结束日期", Modifier.weight(1f)); Switch(hasEnd, { hasEnd = it }) }
                                if (hasEnd) OutlinedTextField(endText, { endText = it }, label = { Text("结束日期（包含当天）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = { IconButton(onClick = { chooseDate(endText) { endText = it } }) { Icon(Icons.Outlined.CalendarMonth, "选择结束日期") } })
                            }
                            HorizontalDivider()
                            Text("节假日过滤", style = MaterialTheme.typography.titleMedium)
                            HolidayPolicy.entries.forEach { policy -> Row(Modifier.fillMaxWidth().clickable { draft = draft.copy(holidayPolicy = policy) }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(draft.holidayPolicy == policy, { draft = draft.copy(holidayPolicy = policy) }); Text(policy.label, style = MaterialTheme.typography.bodyMedium)
                            } }
                            Text(if (draft.scheduleType in setOf(UiScheduleType.MONTHLY, UiScheduleType.YEARLY, UiScheduleType.INTERVAL, UiScheduleType.SHIFT))
                                "节假日只过滤当前重复规则，不额外增加日期。官方覆盖2026年，可在日历自定义班休；未知日期保留原规则。"
                                else "仅法定工作日可替代每日/每周星期限制并加入调休补班。官方覆盖2026年，未知日期保留原规则。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            DayOverrideEditor(draft.scheduleType,runCatching { LocalDate.parse(dateText) }.getOrDefault(draft.onceDate),
                                if(hasEnd && draft.scheduleType!=UiScheduleType.ONCE) runCatching { LocalDate.parse(endText) }.getOrNull() else null,
                                draft.shiftCycle,draft.dayOverrides) { values->draft=draft.copy(dayOverrides=values) }
                        }
                        2 -> {
                            if (draft.timingMode == TimingMode.RANDOM_WINDOW) {
                                EditorHeading("一次主提醒", "稍后和窗口末补充均默认关闭")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { UiAlertMode.entries.forEach { mode -> FilterChip(draft.windowAlertMode == mode, { draft = draft.copy(windowAlertMode = mode) }, label = { Text(mode.label) }) } }
                                Text("响铃 ${draft.windowDurationSeconds} 秒", style = MaterialTheme.typography.titleMedium)
                                Slider(value = draft.windowDurationSeconds.toFloat(), onValueChange = { draft = draft.copy(windowDurationSeconds = it.toInt().coerceIn(5,1800)) }, valueRange = 5f..1800f)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(15,30,60,120,300).forEach { seconds -> FilterChip(draft.windowDurationSeconds == seconds, { draft = draft.copy(windowDurationSeconds = seconds) }, label = { Text("${seconds}秒") }) } }
                                Text("稍后提醒（会增加响铃）", style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf<Int?>(null,5,10,15).forEach { value -> FilterChip(draft.windowSnoozeMinutes == value, { draft = draft.copy(windowSnoozeMinutes = value) }, label = { Text(value?.let { "${it}分钟" } ?: "关闭") }) } }
                                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("窗口末补充提醒"); Text("确认收到后取消；停止声音仍保留", style = MaterialTheme.typography.bodySmall) }; Switch(draft.windowBackupEnabled, { draft = draft.copy(windowBackupEnabled = it) }) }
                                Text(if (draft.windowBackupEnabled) "主提醒若正好选在末分钟，则合并为一次；否则末分钟最多补充一次。" else "仅一个随机主提醒；停止后不会自动重响。", style = MaterialTheme.typography.bodySmall)
                            } else {
                            EditorHeading("提醒几次", "点选一条修改参数；每条可独立设置响铃与稍后")
                            if(draft.scheduleType == UiScheduleType.SHIFT) Text("每个轮班时刻都使用这些提醒点。例如2个时刻×2个提醒点会产生4次提示；按需添加。",style=MaterialTheme.typography.bodySmall)
                            draft.reminderPoints.forEachIndexed { index, point ->
                                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().clickable { pointIndex = index }) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(importOffsetLabel(point.offsetMinutes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                            Text("${point.alertMode.label} · ${point.durationSeconds}秒 · ${point.snoozeMinutes?.let { "稍后${it}分钟" } ?: "稍后关闭"}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Icon(Icons.Outlined.Edit, "编辑提醒点")
                                        IconButton(enabled = draft.reminderPoints.size > 1, onClick = { draft = draft.copy(reminderPoints = draft.reminderPoints.filterIndexed { i, _ -> i != index }) }) { Icon(Icons.Outlined.Close, "移除此提醒点") }
                                    }
                                }
                            }
                            OutlinedButton(enabled = draft.reminderPoints.size < 32, onClick = { pointIndex = -1 }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Text("添加提醒点", Modifier.padding(start = 8.dp)) }
                            Text("至少1条，最多32条。提前量直接输入分钟、小时或天，无需换算。准时与提前规则不允许重复。", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        3 -> {
                            EditorHeading("用什么音乐", "只选择随机范围，响铃时才实际选曲")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { UiMusicPoolMode.entries.forEach { mode -> FilterChip(draft.musicPoolMode == mode, { draft = draft.copy(musicPoolMode = mode) }, label = { Text(mode.label) }) } }
                            Text("一轮内不重复，当前会话循环一首；没有可用音乐时使用内置铃声。", style = MaterialTheme.typography.bodySmall)
                            if (draft.musicPoolMode == UiMusicPoolMode.CUSTOM) {
                                OutlinedTextField(musicSearch, { musicSearch = it }, label = { Text("搜索自选歌曲") }, modifier = Modifier.fillMaxWidth())
                                val usable = tracks.filter { it.enabled && it.available }
                                Text("已选 ${draft.customTrackIds.intersect(usable.map { it.id }.toSet()).size} 首", style = MaterialTheme.typography.titleSmall)
                                if (usable.isEmpty()) Text("先到音乐页导入并启用歌曲", color = MaterialTheme.colorScheme.error)
                                usable.filter { it.title.contains(musicSearch, true) }.forEach { track ->
                                    Row(Modifier.fillMaxWidth().clickable { draft = draft.copy(customTrackIds = if (track.id in draft.customTrackIds) draft.customTrackIds - track.id else draft.customTrackIds + track.id) }, verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(track.id in draft.customTrackIds, { checked -> draft = draft.copy(customTrackIds = if (checked) draft.customTrackIds + track.id else draft.customTrackIds - track.id) })
                                        Column { Text(track.title); Text(track.subtitle, style = MaterialTheme.typography.bodySmall) }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
                if(onPreview!=null) OutlinedButton(onClick={prepareDraft(true)},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp).testTag("schedule_preview_open")) { Text("预览未来安排") }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(modifier = Modifier.weight(2f), onClick = {
                        prepareDraft(false)
                    }) { Text("保存提醒") }
                }
            }
        }
    }
    previewDraft?.let { candidate -> onPreview?.let { loader -> SchedulePreviewDialog(candidate,loader) { previewDraft=null } } }
    pendingShiftSave?.let { candidate ->
        AlertDialog(onDismissRequest={pendingShiftSave=null},title={Text("确认轮班提醒")},text={
            Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("${candidate.title} · ${candidate.outputMode.label}\n${candidate.shiftCycle.days.size}天一个周期，第1天为${candidate.onceDate}\n每个轮班时刻使用${candidate.reminderPoints.size}个提醒点。")
                candidate.shiftCycle.days.forEachIndexed { index,day -> Text("第${index+1}天 ${day.label}："+if(!day.enabled) "关闭提醒" else day.times.joinToString("、") { (if(it.nextDay) "次日 " else "")+ReminderClock.format(it.time) }) }
                Text("休息日按上方配置独立控制；次日时刻归属原班次开始日。可先返回使用“预览未来安排”核对日历过滤与提前量，保存后再查看系统安排。",style=MaterialTheme.typography.bodySmall)
            }
        },confirmButton={TextButton(onClick={pendingShiftSave=null;onSave(candidate)}){Text("确认保存轮班")}},dismissButton={TextButton(onClick={pendingShiftSave=null}){Text("返回修改")}})
    }
    pendingSave?.let { candidate ->
        AlertDialog(onDismissRequest = { pendingSave = null }, title = { Text("确认随机时间窗") }, text = {
            Text("${candidate.title} · ${candidate.outputMode.label}\n${candidate.eventTime.format(clockFormat)} — ${if (candidate.windowCrossesMidnight) "次日 " else ""}${candidate.windowEndTime.format(clockFormat)}\n${candidate.scheduleType.label} · 随机主提醒1次\n" +
                (candidate.windowSnoozeMinutes?.let { "允许稍后${it}分钟，最多3次\n" } ?: "稍后关闭\n") +
                (if (candidate.windowBackupEnabled) "窗口末最多补充1次；停止声音不取消补充\n" else "无补充提醒\n") +
                "音乐池：${candidate.musicPoolMode.label}。切换模式保留原固定提醒点。")
        }, confirmButton = { TextButton(onClick = { pendingSave = null; onSave(candidate) }) { Text("确认保存") } }, dismissButton = { TextButton(onClick = { pendingSave = null }) { Text("返回修改") } })
    }
    pointIndex?.let { index ->
        ReminderPointDialog(draft.reminderPoints.getOrNull(index) ?: ReminderPointDraftUi(alertMode = draft.alertMode, durationSeconds = draft.durationSeconds, snoozeMinutes = draft.snoozeMinutes),
            gentleDefaultSeconds, strongDefaultSeconds, onDismiss = { pointIndex = null }, onSave = { point, all ->
                if (draft.reminderPoints.withIndex().any { it.index != index && it.value.offsetMinutes == point.offsetMinutes }) "同一提前量已存在，请选择不同时间"
                else {
                    val points = draft.reminderPoints.toMutableList().apply { if (index >= 0) set(index, point) else add(point) }
                    draft = draft.copy(reminderPoints = if (all) points.map { it.copy(alertMode = point.alertMode, durationSeconds = point.durationSeconds, snoozeMinutes = point.snoozeMinutes) } else points,
                        alertMode = point.alertMode, durationSeconds = point.durationSeconds, snoozeMinutes = point.snoozeMinutes)
                    pointIndex = null; null
                }
            })
    }
    if (deleteConfirmation) AlertDialog(onDismissRequest = { deleteConfirmation = false }, title = { Text("移入回收站？") }, text = { Text("此事项会停用，待触发提醒与稍后提醒会取消，响铃历史保留。可在设置中心的回收站恢复，恢复后默认停用。") },
        confirmButton = { TextButton(onClick = { draft.id?.let { onDelete?.invoke(it) }; deleteConfirmation = false }) { Text("移入回收站") } },
        dismissButton = { TextButton(onClick = { deleteConfirmation = false }) { Text("取消") } })
}

@Composable
private fun EditorHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderPointDialog(initial: ReminderPointDraftUi, gentle: Int, strong: Int, onDismiss: () -> Unit,
    onSave: (ReminderPointDraftUi, Boolean) -> String?) {
    val advance = -initial.offsetMinutes
    var unit by remember { mutableIntStateOf(if (advance != 0 && advance % 1440 == 0) 1440 else if (advance != 0 && advance % 60 == 0) 60 else 1) }
    var amount by remember { mutableStateOf((advance / unit).toString()) }
    var mode by remember { mutableStateOf(initial.alertMode) }
    var duration by remember { mutableStateOf(initial.durationSeconds.toString()) }
    var snooze by remember { mutableStateOf(initial.snoozeMinutes) }
    var all by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("提醒点参数") }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(amount, { amount = it }, label = { Text("提前量（0表示准时）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(1 to "分钟", 60 to "小时", 1440 to "天").forEach { (value, label) ->
                FilterChip(unit == value, { unit = value }, label = { Text(label) })
            } }
            Text("最多提前3650天；例如 7 + 天 = 提前一周", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { UiAlertMode.entries.forEach { value -> FilterChip(mode == value, { mode = value; duration = (if (mode == UiAlertMode.STRONG) strong else gentle).toString() }, label = { Text(value.label) }) } }
            OutlinedTextField(duration, { duration = it }, label = { Text("响铃时长（5～1800秒）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Text("稍后提醒", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf<Int?>(null, 5, 10, 15).forEach { minutes ->
                FilterChip(snooze == minutes, { snooze = minutes }, label = { Text(minutes?.let { "${it}分钟" } ?: "关闭") })
            } }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(all, { all = it }); Text("强度、时长、稍后应用到全部提醒点", style = MaterialTheme.typography.bodySmall) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        val value = amount.toLongOrNull()
        val seconds = duration.toIntOrNull()
        error = when {
            value == null || value < 0 || value > 5_256_000L / unit -> "提前量超出0～3650天的范围"
            seconds == null || seconds !in 5..1800 -> "时长须为5～1800秒"
            else -> onSave(ReminderPointDraftUi(-(value * unit).toInt(), mode, seconds, snooze), all)
        }
    }) { Text("保存此提醒点") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
