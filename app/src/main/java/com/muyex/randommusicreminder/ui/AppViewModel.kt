package com.muyex.randommusicreminder.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.muyex.randommusicreminder.app.AppContainer
import com.muyex.randommusicreminder.data.settings.AppSettings
import com.muyex.randommusicreminder.domain.model.AlertMode
import com.muyex.randommusicreminder.domain.model.EventDetails
import com.muyex.randommusicreminder.domain.model.MusicPoolMode
import com.muyex.randommusicreminder.domain.model.MusicTrack
import com.muyex.randommusicreminder.domain.model.ReminderEvent
import com.muyex.randommusicreminder.domain.model.ReminderRule
import com.muyex.randommusicreminder.domain.model.ScheduleType
import com.muyex.randommusicreminder.domain.model.ScheduledOccurrence
import com.muyex.randommusicreminder.domain.model.OccurrenceKind
import com.muyex.randommusicreminder.domain.model.ScheduleMode
import com.muyex.randommusicreminder.domain.model.WeekdayMask
import com.muyex.randommusicreminder.domain.ringing.RingHistoryEntry
import com.muyex.randommusicreminder.music.ImportedMusic
import com.muyex.randommusicreminder.domain.music.PreviewOptions
import com.muyex.randommusicreminder.domain.statistics.DailyUsage
import com.muyex.randommusicreminder.domain.calendar.HolidayPolicy
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val selectedSection = MutableStateFlow(AppSection.REMINDERS)
    private val editingEvent = MutableStateFlow<EventDraftUi?>(null)
    private val completionChoices=MutableStateFlow<com.muyex.randommusicreminder.domain.tasks.TaskChoiceList?>(null)
    private var completionRequestGeneration=0L
    private val previewingTrackId = MutableStateFlow<Long?>(null)
    private var previewRequestGeneration = 0L
    private val isImporting = MutableStateFlow(false)
    private val importProgress = MutableStateFlow<MusicImportProgress?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val organizerBusy = MutableStateFlow(false)

    private val repositoryState = combine(
        container.eventRepository.observeEvents().catch {
            message.value = "读取提醒失败：${it.userMessage()}"
            emit(emptyList())
        },
        container.musicRepository.observeTracks().catch {
            message.value = "读取音乐库失败：${it.userMessage()}"
            emit(emptyList())
        },
        container.settingsRepository.settings.catch {
            message.value = "读取设置失败：${it.userMessage()}"
            emit(AppSettings())
        },
        container.eventRepository.observeScheduledOccurrences().catch {
            message.value = "读取下次提醒失败：${it.userMessage()}"
            emit(emptyList())
        },
        container.eventRepository.observeRingHistory().catch {
            message.value = "读取响铃历史失败：${it.userMessage()}"
            emit(emptyList())
        },
    ) { events, tracks, settings, occurrences, history ->
        RepositoryState(
            events = events,
            tracks = tracks,
            settings = settings,
            occurrences = occurrences,
            history = history,
            isLoading = false,
        )
    }.combine(container.usageRepository.observeDaily().catch {
        message.value = "读取使用统计失败：${it.userMessage()}"
        emit(emptyList())
    }) { base, usage -> base.copy(dailyUsage = usage) }.combine(container.eventRepository.observeDeletedEvents().catch {
        message.value="读取回收站失败：${it.userMessage()}";emit(emptyList())
    }) { base, deleted -> base.copy(deletedEvents=deleted) }.combine(container.taskRepository.observeRecords().catch { message.value="读取完成记录失败";emit(emptyList()) }) { base, records -> base.copy(taskRecords=records) }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = RepositoryState(),
    )

    private val navigationState = combine(selectedSection, editingEvent,completionChoices) { section, editor,choices ->
        NavigationState(section, editor,choices)
    }

    private val operationState = combine(
        previewingTrackId,
        isImporting,
        message,
        importProgress,
    ) { previewTrackId, importing, currentMessage, progress ->
        OperationState(previewTrackId, importing, currentMessage, progress)
    }.combine(organizerBusy) { state, busy -> state.copy(organizerBusy=busy) }

    val uiState = combine(
        repositoryState,
        navigationState,
        operationState,
        flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(5_000L)
            }
        },
    ) { repository, navigation, operation, now ->
        val upcoming = repository.occurrences
            .filter { it.triggerAtEpochMillis >= now - 15 * 60_000L }
            .groupBy { it.eventId }
        AppUiState(
            selectedSection = navigation.section,
            events = repository.events.map { details ->
                details.toListItem().copy(
                    nextAtEpochMillis = upcoming[details.event.id]?.minOfOrNull { it.triggerAtEpochMillis },
                    selectionText = upcoming[details.event.id]?.filter { it.selectedLocalMinute != null && !it.windowBackup }?.minByOrNull { it.triggerAtEpochMillis }
                        ?.let { "本次已选 " + Instant.ofEpochMilli(it.triggerAtEpochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) },
                    hasActiveBackup = upcoming[details.event.id].orEmpty().any { it.windowBackup && (it.windowStartAtEpochMillis ?: Long.MAX_VALUE) <= now && (it.windowEndAtEpochMillis ?: 0) >= now },
                    nextReminderText = if (!details.event.enabled) {
                        "提醒已暂停"
                    } else {
                        upcoming[details.event.id]?.minByOrNull { it.triggerAtEpochMillis }
                            ?.nextReminderLabel(now, details.event.hideWindowSelection) ?: "暂无待触发提醒"
                    },
                )
            },
            deletedEvents = repository.deletedEvents.map { it.toListItem() },
            organizerBusy = operation.organizerBusy,
            completionChoices=navigation.completionChoices,taskRecords=repository.taskRecords,
            tracks = repository.tracks.map(MusicTrack::toListItem),
            settings = repository.settings.toUi(),
            editingEvent = navigation.editor,
            previewingTrackId = operation.previewingTrackId,
            isLoading = repository.isLoading,
            isImporting = operation.isImporting,
            message = operation.message,
            ringHistory = repository.history,
            importProgress = operation.importProgress,
            dailyUsage = repository.dailyUsage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState(),
    )

    init {
        viewModelScope.launch {
            container.usageTracker.error.collect { it?.let { error -> message.value = error } }
        }
        viewModelScope.launch {
            container.musicPreviewController.state.collect { previewState ->
                previewState.errorMessage?.let {
                    previewingTrackId.value = null
                    message.value = it
                }
                if (previewState.uri == null) previewingTrackId.value = null
            }
        }
    }

    fun selectSection(section: AppSection) {
        if (section != AppSection.MUSIC) stopPreview()
        selectedSection.value = section
    }

    fun createEvent() {
        val settings = repositoryState.value.settings
        editingEvent.value = EventDraftUi(
            onceDate = LocalDate.now(),
            durationSeconds = settings.gentleDurationSeconds,
            snoozeMinutes = settings.defaultSnoozeMinutes,
            reminderPoints = listOf(
                ReminderPointDraftUi(
                    offsetMinutes = 0,
                    alertMode = UiAlertMode.GENTLE,
                    durationSeconds = settings.gentleDurationSeconds,
                    snoozeMinutes = settings.defaultSnoozeMinutes,
                ),
            ),
        )
    }

    fun editEvent(eventId: Long) {
        viewModelScope.launch {
            runCatching { container.eventRepository.getEvent(eventId) }
                .onSuccess { details ->
                    editingEvent.value = details?.toDraftUi()
                    if (details == null) message.value = "这个提醒已经不存在"
                }
                .onFailure { message.value = "读取提醒失败：${it.userMessage()}" }
        }
    }

    fun closeEditor() {
        editingEvent.value = null
    }

    fun updateEventDraft(draft: EventDraftUi) {
        editingEvent.value = draft
    }

    suspend fun previewSchedule(draft:EventDraftUi)=container.schedulePreviewRepository.preview(draft.toDetails())

    private var saveEventJob: kotlinx.coroutines.Job? = null
    fun saveEvent(draft: EventDraftUi) {
        if (saveEventJob?.isActive == true) return
        saveEventJob = viewModelScope.launch {
            runCatching { container.saveReminder(draft.toDetails()) }.getOrElse {
                message.value = "保存提醒失败：${it.userMessage()}"
                return@launch
            }
            editingEvent.value = null
            message.value = if (runCatching { container.scheduleReconciler.reconcileAll() }.isSuccess) {
                "提醒已保存"
            } else {
                "提醒已保存，但系统重排失败；请检查权限后重新打开应用"
            }
        }
    }

    fun acknowledgeWindow(eventId: Long) {
        viewModelScope.launch {
            runCatching {
                val now=System.currentTimeMillis()
                val backup=container.eventRepository.observeScheduledOccurrences().first().firstOrNull { it.eventId==eventId && it.windowBackup && (it.windowStartAtEpochMillis ?: Long.MAX_VALUE)<=now && (it.windowEndAtEpochMillis ?: 0)>=now }
                    ?: error("没有待取消的当前窗口补充")
                container.eventRepository.acknowledgeWindow(backup.id)
                container.scheduleReconciler.reconcileAll()
            }.onSuccess { message.value="已确认收到并取消本次窗口补充；之后的窗口保留" }
                .onFailure { message.value="确认未完成：${it.userMessage()}" }
        }
    }

    fun setEventEnabled(eventId: Long, enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                check(container.eventRepository.setEnabled(eventId, enabled)) { "提醒不存在" }
            }.getOrElse {
                message.value = "更新提醒失败：${it.userMessage()}"
                return@launch
            }
            if (runCatching { container.scheduleReconciler.reconcileAll() }.isFailure) {
                message.value = "提醒状态已更新，但系统重排失败；请检查权限后重新打开应用"
            }
        }
    }

    fun deleteEvent(eventId: Long) {
        viewModelScope.launch {
            runCatching {
                check(container.eventRepository.deleteEvent(eventId)) { "提醒不存在" }
            }.getOrElse {
                message.value = "删除提醒失败：${it.userMessage()}"
                return@launch
            }
            editingEvent.value = null
            message.value = if (runCatching { container.scheduleReconciler.reconcileAll() }.isSuccess) {
                "已移入回收站"
            } else {
                "已移入回收站，但系统重排失败；请重新打开应用完成清理"
            }
        }
    }

    fun openTaskCompletion(eventId:Long,range:com.muyex.randommusicreminder.domain.tasks.TaskChoiceRange=com.muyex.randommusicreminder.domain.tasks.TaskChoiceRange.UPCOMING) {
        val request=++completionRequestGeneration
        viewModelScope.launch {
            runCatching { container.taskRepository.choices(eventId,range) }.onSuccess { if(request==completionRequestGeneration) completionChoices.value=it }
                .onFailure { if(request==completionRequestGeneration) message.value=it.userMessage() }
        }
    }
    fun dismissTaskCompletion() { completionRequestGeneration++;completionChoices.value=null }
    fun completeTask(choice:com.muyex.randommusicreminder.domain.tasks.TaskChoice) = organize {
        val result=container.taskRepository.complete(choice)
        dismissTaskCompletion()
        (if(result.changed) "本次事项已完成，下一周期保留" else "本次已经完成")+(if(result.rescheduled) "" else "；已保存，但系统重排失败，请重新打开应用核对")
    }
    fun reopenTask(id:Long)=organize {
        val result=container.taskRepository.reopen(id)
        "已撤销完成，只恢复当前规则的未来提醒"+(if(result.rescheduled) "" else "；已保存，但系统重排失败，请重新打开应用核对")
    }

    fun restoreEvent(id: Long) = organize {
        check(container.eventRepository.restoreEvent(id)) { "事项已恢复或移除" }
        "已恢复并保持停用，请在提醒页核对配置后启用"
    }

    fun permanentlyDeleteEvents(ids: List<Long>) = organize {
        val count=container.eventRepository.permanentlyDeleteEvents(ids)
        "已永久删除${count}项；响铃历史保留"
    }

    fun renameTag(old: String, new: String) = organize {
        "已更新${container.eventRepository.renameTag(old,new)}项的标签"
    }

    fun removeTag(tag: String) = organize {
        "已从${container.eventRepository.removeTag(tag)}项移除标签，事项保留"
    }

    private fun organize(action: suspend () -> String) {
        if(organizerBusy.value) return
        organizerBusy.value=true
        viewModelScope.launch {
            try { message.value=action() } catch(error: Exception) { message.value="操作未完成：${error.userMessage()}" }
            finally { organizerBusy.value=false }
        }
    }

    fun testReminder(eventId: Long) {
        viewModelScope.launch {
            stopPreview()
            runCatching { container.scheduleTestReminder(eventId) }
                .onSuccess { message.value = "已安排10秒后测试所选提醒方式，最多2秒；音乐音量5%，仅震动会驱动马达" }
                .onFailure { message.value = "试响安排失败：${it.userMessage()}" }
        }
    }

    fun importMusic(uris: List<Uri>) {
        if (isImporting.value || uris.isEmpty()) return
        isImporting.value = true
        importProgress.value = MusicImportProgress(total = uris.size)
        viewModelScope.launch {
            var importedCount = 0
            val failures = mutableListOf<MusicImportFailure>()
            try {
                uris.forEachIndexed { index, uri ->
                    val name = runCatching { container.musicImporter.displayName(uri) }.getOrNull()
                        ?: uri.lastPathSegment.orEmpty().ifBlank { "文件 ${index + 1}" }
                    importProgress.value = MusicImportProgress(uris.size, index, importedCount, name, failures.toList())
                    var imported: ImportedMusic? = null
                    try {
                        val item = container.musicImporter.import(uri).also { imported = it }
                        val duration = item.durationMs?.takeIf { it > 0 } ?: error("无法解码或读取音乐时长")
                        container.musicRepository.saveTrack(MusicTrack(
                            displayName = item.displayName, uri = item.uri.toString(), mimeType = item.mimeType ?: "audio/*",
                            durationMillis = duration, fileSizeBytes = item.fileSizeBytes, importedAtEpochMillis = item.importedAtEpochMs,
                        ))
                        importedCount++
                    } catch (error: Exception) {
                        imported?.let { copy ->
                            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                runCatching { container.musicImporter.remove(copy) }
                            }
                        }
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        failures += MusicImportFailure(name, error.userMessage())
                    }
                    importProgress.value = MusicImportProgress(uris.size, index + 1, importedCount, failures = failures.toList())
                }
                message.value = "导入完成：成功 $importedCount 首，失败 ${failures.size} 首"
            } finally {
                isImporting.value = false
            }
        }
    }

    fun dismissImportProgress() { if (!isImporting.value) importProgress.value = null }

    fun previewMusic(trackId: Long) {
        val request = ++previewRequestGeneration
        viewModelScope.launch {
            runCatching {
                val track = container.musicRepository.getTrack(trackId) ?: error("音乐不存在")
                // 查询曲目期间若用户已离开页面或应用，过期请求不得重新出声。
                if (request != previewRequestGeneration || selectedSection.value != AppSection.MUSIC) return@launch
                val options = repositoryState.value.settings.previewOptions
                options.endPositionMillis(track.durationMillis)
                container.musicPreviewController.play(Uri.parse(track.uri), options, track.durationMillis)
            }.onSuccess {
                previewingTrackId.value = trackId
            }.onFailure {
                message.value = "无法试听：${it.userMessage()}"
            }
        }
    }

    fun stopPreview() {
        previewRequestGeneration++
        container.musicPreviewController.stop()
        previewingTrackId.value = null
    }

    fun setMusicEnabled(trackId: Long, enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                check(container.musicRepository.setEnabled(trackId, enabled)) { "音乐不存在" }
            }.onFailure {
                message.value = "更新音乐失败：${it.userMessage()}"
            }
        }
    }

    fun setMusicEnabledBulk(trackIds: List<Long>, enabled: Boolean) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            runCatching { container.musicRepository.setEnabled(trackIds, enabled) }
                .onSuccess { message.value = "已${if (enabled) "启用" else "停用"} $it 首音乐；失效曲目不会被启用" }
                .onFailure { message.value = "批量更新失败：${it.userMessage()}" }
        }
    }

    fun setPreviewOptions(options: PreviewOptions) {
        stopPreview()
        viewModelScope.launch {
            runCatching { container.settingsRepository.updatePreviewOptions(options) }
                .onSuccess { message.value = "试听参数已保存" }
                .onFailure { message.value = "保存试听参数失败：${it.userMessage()}" }
        }
    }

    fun setAppearance(value: com.muyex.randommusicreminder.domain.settings.AppearanceSettings) {
        viewModelScope.launch { runCatching { container.settingsRepository.updateAppearance(value) }.onFailure { message.value = "外观保存失败：${it.userMessage()}" } }
    }

    fun setUsageStatsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { container.settingsRepository.updateUsageStatsEnabled(enabled) }
                .onFailure { message.value = "保存统计设置失败：${it.userMessage()}" }
        }
    }

    fun deleteMusic(trackId: Long) {
        viewModelScope.launch {
            runCatching {
                val track = container.musicRepository.getTrack(trackId) ?: error("音乐不存在")
                if (previewingTrackId.value == trackId) stopPreview()
                check(container.musicRepository.deleteTrack(trackId)) { "音乐不存在" }
                track.toImportedMusicOrNull()?.let { container.musicImporter.remove(it) }
            }.onSuccess {
                message.value = "音乐已移除，手机中的原文件不受影响"
            }.onFailure {
                message.value = "移除音乐失败：${it.userMessage()}"
            }
        }
    }

    fun setGentleDuration(seconds: Int) {
        viewModelScope.launch {
            runCatching { container.settingsRepository.updateGentleDuration(seconds) }
                .onFailure { message.value = "保存设置失败：${it.userMessage()}" }
        }
    }

    fun setStrongDuration(seconds: Int) {
        viewModelScope.launch {
            runCatching { container.settingsRepository.updateStrongDuration(seconds) }
                .onFailure { message.value = "保存设置失败：${it.userMessage()}" }
        }
    }

    fun setDefaultSnooze(minutes: Int) {
        viewModelScope.launch {
            runCatching { container.settingsRepository.updateDefaultSnooze(minutes) }
                .onFailure { message.value = "保存设置失败：${it.userMessage()}" }
        }
    }

    fun setNotifyMissed(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { container.settingsRepository.updateNotifyMissedReminders(enabled) }
                .onFailure { message.value = "保存设置失败：${it.userMessage()}" }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    override fun onCleared() {
        stopPreview()
    }

    class Factory(
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AppViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return AppViewModel(container) as T
        }
    }
}

private data class RepositoryState(
    val events: List<EventDetails> = emptyList(),
    val deletedEvents: List<EventDetails> = emptyList(),
    val taskRecords:List<com.muyex.randommusicreminder.domain.tasks.TaskActionRecord> = emptyList(),
    val tracks: List<MusicTrack> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val occurrences: List<ScheduledOccurrence> = emptyList(),
    val history: List<RingHistoryEntry> = emptyList(),
    val dailyUsage: List<DailyUsage> = emptyList(),
)

private fun ScheduledOccurrence.nextReminderLabel(now: Long, hideSelection: Boolean = true): String {
    if (selectedLocalMinute != null && kind == OccurrenceKind.ORIGINAL && hideSelection && !windowBackup) {
        val format = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        fun clock(value: Long?) = value?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(format) }.orEmpty()
        return "下次时间窗 ${clock(windowStartAtEpochMillis)} — ${clock(windowEndAtEpochMillis)} · 已安排"
    }
    val time = Instant.ofEpochMilli(triggerAtEpochMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
    val kindLabel = when (kind) {
        OccurrenceKind.ORIGINAL -> "下次提醒"
        OccurrenceKind.SNOOZE -> "稍后提醒"
        OccurrenceKind.TEST -> "低音量试响"
    }
    val status = when {
        scheduleMode == ScheduleMode.NONE -> " · 正在安排"
        triggerAtEpochMillis < now -> " · 等待系统触发"
        scheduleMode == ScheduleMode.INEXACT -> " · 可能延迟"
        else -> ""
    }
    return "$kindLabel $time$status" + sourceLabel?.let { "\n$it" }.orEmpty()
}

private data class NavigationState(
    val section: AppSection,
    val editor: EventDraftUi?,
    val completionChoices:com.muyex.randommusicreminder.domain.tasks.TaskChoiceList?,
)

private data class OperationState(
    val previewingTrackId: Long?,
    val isImporting: Boolean,
    val message: String?,
    val importProgress: MusicImportProgress?,
    val organizerBusy: Boolean = false,
)

private fun EventDetails.toListItem(): ReminderListItem {
    return ReminderListItem(
        id = event.id,
        title = event.title, note = event.note, createdAtEpochMillis = event.createdAtEpochMillis, updatedAtEpochMillis = event.updatedAtEpochMillis,
        tags=event.tags,deletedAtEpochMillis=event.deletedAtEpochMillis,outputMode=event.outputMode,trackCompletion=event.trackCompletion,
        scheduleText = event.scheduleText()+(if(event.dayOverrides.isEmpty()) "" else " · ${event.dayOverrides.size}个单日调整"),
        timeText = if(event.scheduleType == ScheduleType.SHIFT) "按每日轮班时刻 · ${rules.size}个提醒点" else if (event.timingMode == com.muyex.randommusicreminder.domain.model.TimingMode.RANDOM_WINDOW)
            "随机主提醒1次" + (if (event.windowSnoozeMinutes == null) " · 稍后关闭" else " · 允许稍后") +
                (if (event.windowBackupEnabled) " · 窗口末补充1次，确认收到取消" else " · 无补充提醒")
            else rules.sortedBy { it.offsetMinutes }.joinToString("、") { rule ->
            rule.offsetLabel(event.localTime)
        }.ifBlank { "未设置时间点" },
        eventTimeText = if(event.scheduleType == ScheduleType.SHIFT) "${event.shiftCycle?.days?.size ?: 0}天轮班" else com.muyex.randommusicreminder.domain.time.ReminderClock.format(event.localTime) +
            if (event.timingMode == com.muyex.randommusicreminder.domain.model.TimingMode.RANDOM_WINDOW)
                " — ${if (event.windowCrossesMidnight) "次日 " else ""}${event.windowEndTime.format(DateTimeFormatter.ofPattern("HH:mm"))}" else "",
        randomWindow = event.timingMode == com.muyex.randommusicreminder.domain.model.TimingMode.RANDOM_WINDOW,
        alertMode = if (activeRules.any { it.alertMode == AlertMode.STRONG }) {
            UiAlertMode.STRONG
        } else {
            UiAlertMode.GENTLE
        },
        durationSeconds = activeRules.maxOfOrNull { it.ringDurationSeconds } ?: 30,
        musicText = if(!event.outputMode.audio) { if(event.outputMode.vibration && event.haptics.useMusicRhythm) "只取音乐节奏，不播放音乐" else "不播放音乐" } else if (event.musicPoolMode == MusicPoolMode.GLOBAL) {
            "全部音乐随机"
        } else {
            "自选 ${customTrackIds.size} 首"
        },
        enabled = event.enabled,
        holidayPolicy = event.holidayPolicy,
    )
}

private fun EventDetails.toDraftUi(): EventDraftUi {
    val firstRule = rules.firstOrNull()
    val points = rules.map { rule ->
        ReminderPointDraftUi(
            offsetMinutes = rule.offsetMinutes,
            alertMode = rule.alertMode.toUi(),
            durationSeconds = rule.ringDurationSeconds,
            snoozeMinutes = rule.snoozeMinutes,
        )
    }
    return EventDraftUi(
        id = event.id,
        title = event.title,
        note = event.note,
        scheduleType = event.scheduleType.toUi(),
        onceDate = event.localDate,
        intervalDays = event.intervalDays, missingDayPolicy = event.missingDayPolicy, endDate = event.endDate,
        eventTime = event.localTime,
        timingMode = event.timingMode, windowEndTime = event.windowEndTime, windowCrossesMidnight = event.windowCrossesMidnight,
        windowAlertMode = event.windowAlertMode.toUi(), windowDurationSeconds = event.windowDurationSeconds,
        windowSnoozeMinutes = event.windowSnoozeMinutes, windowBackupEnabled = event.windowBackupEnabled,
        hideWindowSelection = event.hideWindowSelection, windowFirstNotBeforeEpochMillis = event.windowFirstNotBeforeEpochMillis,
        shiftCycle = event.shiftCycle ?: com.muyex.randommusicreminder.domain.planner.ShiftCycle.template(),
        tags=event.tags,outputMode=event.outputMode,haptics=event.haptics,trackCompletion=event.trackCompletion,dayOverrides=event.dayOverrides,
        weeklyDays = DayOfWeek.entries.filterTo(linkedSetOf()) {
            WeekdayMask.contains(event.weekdaysMask, it)
        },
        reminderPoints = points,
        alertMode = firstRule?.alertMode.toUi(),
        durationSeconds = firstRule?.ringDurationSeconds ?: 30,
        snoozeMinutes = firstRule?.snoozeMinutes,
        musicPoolMode = event.musicPoolMode.toUi(),
        customTrackIds = customTrackIds,
        enabled = event.enabled,
        holidayPolicy = event.holidayPolicy,
    )
}

private fun EventDraftUi.toDetails(): EventDetails {
    require(reminderPoints.isNotEmpty()) { "至少需要一个提醒时间点" }
    require(reminderPoints.all { it.offsetMinutes <= 0 }) { "提醒点只能准时或提前" }
    require(reminderPoints.map { it.offsetMinutes }.distinct().size == reminderPoints.size) {
        "同一事件不能添加重复提醒点"
    }
    return EventDetails(
        event = ReminderEvent(
            id = id ?: 0,
            title = title.trim(),
            note = note.trim(),
            enabled = enabled,
            holidayPolicy = holidayPolicy,
            scheduleType = scheduleType.toDomain(),
            localDate = onceDate,
            localTime = eventTime,
            weekdaysMask = if (scheduleType == UiScheduleType.WEEKLY) {
                WeekdayMask.from(weeklyDays)
            } else {
                WeekdayMask.NONE
            },
            musicPoolMode = musicPoolMode.toDomain(),
            intervalDays = intervalDays, missingDayPolicy = missingDayPolicy, endDate = endDate,
            timingMode = timingMode, windowEndTime = windowEndTime, windowCrossesMidnight = windowCrossesMidnight,
            windowAlertMode = windowAlertMode.toDomain(), windowDurationSeconds = windowDurationSeconds,
            windowSnoozeMinutes = windowSnoozeMinutes, windowBackupEnabled = windowBackupEnabled,
            hideWindowSelection = hideWindowSelection, windowFirstNotBeforeEpochMillis = windowFirstNotBeforeEpochMillis,
            shiftCycle = if(scheduleType == UiScheduleType.SHIFT) shiftCycle else null,
            tags=tags,outputMode=outputMode,haptics=haptics,trackCompletion=trackCompletion,dayOverrides=dayOverrides,
        ),
        // 数据层以事件时刻为基准，负偏移可自然覆盖跨日和提前若干天的提醒。
        rules = reminderPoints.sortedBy { it.offsetMinutes }.map { point ->
            ReminderRule(
                offsetMinutes = point.offsetMinutes,
                alertMode = point.alertMode.toDomain(),
                ringDurationSeconds = point.durationSeconds.coerceIn(5, 1_800),
                snoozeMinutes = point.snoozeMinutes,
            )
        },
        customTrackIds = if (musicPoolMode == UiMusicPoolMode.CUSTOM) customTrackIds else emptySet(),
    )
}

private fun ReminderEvent.scheduleText(): String = (when (scheduleType) {
    ScheduleType.ONCE -> "单次 · $localDate"
    ScheduleType.DAILY -> "每天 · 从 $localDate 开始"
    ScheduleType.MONTHLY -> "每月${localDate.dayOfMonth}日 · ${if (missingDayPolicy == com.muyex.randommusicreminder.domain.model.MissingDayPolicy.SKIP) "缺日跳过" else "缺日取月末"}"
    ScheduleType.YEARLY -> "每年${localDate.monthValue}月${localDate.dayOfMonth}日 · ${if (missingDayPolicy == com.muyex.randommusicreminder.domain.model.MissingDayPolicy.SKIP) "缺日跳过" else "缺日取月末"}"
    ScheduleType.INTERVAL -> "每${intervalDays}天 · 从 $localDate 开始"
    ScheduleType.SHIFT -> "${shiftCycle?.days?.size ?: 0}天轮班 · 第1天 $localDate"
    ScheduleType.WEEKLY -> DayOfWeek.entries
        .filter { WeekdayMask.contains(weekdaysMask, it) }
        .joinToString(prefix = "每周 · ", separator = "、") { it.chineseName() } +
        " · 从 $localDate 开始"
}) + if (holidayPolicy != HolidayPolicy.NONE) " · ${holidayPolicy.label}（官方2026＋自定义日历，未覆盖日期按原规则）" else ""

private fun ReminderRule.offsetLabel(eventTime: LocalTime): String {
    val advance = (-offsetMinutes).coerceAtLeast(0)
    val clock = eventTime.minusMinutes(advance.toLong())
    val clockText = com.muyex.randommusicreminder.domain.time.ReminderClock.format(clock)
    return if (advance == 0) "准时 $clockText" else "提前${formatAdvance(advance)} $clockText"
}

private fun formatAdvance(totalMinutes: Int): String {
    val days = totalMinutes / (24 * 60)
    val hours = totalMinutes % (24 * 60) / 60
    val minutes = totalMinutes % 60
    return buildList {
        if (days > 0) add("${days}天")
        if (hours > 0) add("${hours}小时")
        if (minutes > 0) add("${minutes}分钟")
    }.joinToString("").ifBlank { "0分钟" }
}

private fun MusicTrack.toListItem(): MusicListItem {
    val totalSeconds = durationMillis / 1_000
    val duration = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    val type = mimeType.substringAfter('/').uppercase().ifBlank { "音频" }
    return MusicListItem(
        id = id,
        title = displayName,
        subtitle = invalidReason ?: "$duration · $type",
        enabled = enabled,
        available = invalidReason == null,
        durationMillis = durationMillis,
        fileSizeBytes = fileSizeBytes,
        importedAtEpochMillis = importedAtEpochMillis,
        uri = uri,
    )
}

private fun AppSettings.toUi() = SettingsUi(
    gentleDurationSeconds = gentleDurationSeconds,
    strongDurationSeconds = strongDurationSeconds,
    defaultSnoozeMinutes = defaultSnoozeMinutes,
    notifyMissedReminders = notifyMissedReminders,
    previewOptions = previewOptions,
    usageStatsEnabled = usageStatsEnabled,
    appearance = appearance,
)

private fun MusicTrack.toImportedMusicOrNull(): ImportedMusic? {
    val parsedUri = Uri.parse(uri)
    if (parsedUri.scheme != "file") return null
    val file = parsedUri.path?.let(::File) ?: return null
    return ImportedMusic(
        id = file.nameWithoutExtension,
        displayName = displayName,
        uri = parsedUri,
        mimeType = mimeType,
        durationMs = durationMillis,
        fileSizeBytes = fileSizeBytes.takeIf { it > 0 } ?: file.length(),
        importedAtEpochMs = importedAtEpochMillis,
    )
}

private fun ScheduleType.toUi() = UiScheduleType.valueOf(name)
private fun UiScheduleType.toDomain() = ScheduleType.valueOf(name)
private fun AlertMode?.toUi() = UiAlertMode.valueOf((this ?: AlertMode.GENTLE).name)
private fun UiAlertMode.toDomain() = AlertMode.valueOf(name)
private fun MusicPoolMode.toUi() = UiMusicPoolMode.valueOf(name)
private fun UiMusicPoolMode.toDomain() = MusicPoolMode.valueOf(name)

private fun DayOfWeek.chineseName(): String = when (this) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}

private fun Throwable.userMessage(): String = localizedMessage
    ?.takeIf { it.isNotBlank() }
    ?: "未知错误"
