package com.muyex.randommusicreminder.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.ActivityManager
import android.os.PowerManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAlarm
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import com.muyex.randommusicreminder.ui.screens.SettingsHubScreen
import com.muyex.randommusicreminder.ui.screens.CompletionHistoryScreen
import com.muyex.randommusicreminder.ui.components.TaskCompletionDialog
import com.muyex.randommusicreminder.ui.screens.AppearanceScreen
import com.muyex.randommusicreminder.ui.screens.TagsScreen
import com.muyex.randommusicreminder.ui.screens.TrashScreen
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muyex.randommusicreminder.ui.components.EventEditorDialog
import com.muyex.randommusicreminder.playback.ReminderNotificationFactory
import com.muyex.randommusicreminder.ui.screens.MusicScreen
import com.muyex.randommusicreminder.ui.screens.ReminderScreen
import com.muyex.randommusicreminder.ui.screens.SettingsScreen
import com.muyex.randommusicreminder.ui.screens.RingHistoryScreen
import com.muyex.randommusicreminder.ui.screens.StatisticsScreen
import com.muyex.randommusicreminder.ui.screens.CalendarScreen
import com.muyex.randommusicreminder.ui.screens.DataScreen
import com.muyex.randommusicreminder.ui.screens.AiScreen
import androidx.activity.compose.BackHandler

private data class NavigationItem(
    val section: AppSection,
    val icon: ImageVector,
)

private val navigationItems = listOf(
    NavigationItem(AppSection.REMINDERS, Icons.Outlined.Alarm),
    NavigationItem(AppSection.MUSIC, Icons.Outlined.LibraryMusic),
    NavigationItem(AppSection.CALENDAR, Icons.Outlined.CalendarMonth),
    NavigationItem(AppSection.SETTINGS, Icons.Outlined.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LexingApp(viewModel: AppViewModel, toolsViewModel: ToolsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toolsState by toolsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    var permissionHealth by remember { mutableStateOf(readPermissionHealth(context)) }
    var notificationPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var fullScreenGuidanceShown by rememberSaveable { mutableStateOf(false) }
    var settingsExport by rememberSaveable { mutableStateOf(false) }
    var recoveryExport by rememberSaveable { mutableStateOf(false) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { if (recoveryExport) toolsViewModel.exportRecovery(it) else toolsViewModel.export(it, settingsExport) } }
    val taskCsvPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(toolsViewModel::exportTaskRecords) }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(toolsViewModel::exportStatistics) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(toolsViewModel::readImport) }
    val hasEnabledReminders = state.events.any { it.enabled }
    val hasEnabledStrongReminders = state.events.any { it.enabled && it.alertMode == UiAlertMode.STRONG && it.outputMode != com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode.NOTIFICATION }
    val secondarySection = navigationItems.none { it.section == state.selectedSection }
    val screenStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    BackHandler(enabled = secondarySection) { viewModel.selectSection(AppSection.SETTINGS) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        permissionHealth = readPermissionHealth(context)
    }
    val musicPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        // 持久化读取授权，确保设备重启后既有提醒仍能播放所选音乐。
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        if (uris.isNotEmpty()) viewModel.importMusic(uris)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionHealth = readPermissionHealth(context)
            } else if (event == Lifecycle.Event.ON_STOP) {
                viewModel.stopPreview()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(toolsState.message) {
        toolsState.message?.let { snackbarHostState.showSnackbar(it); toolsViewModel.consumeMessage() }
    }

    LaunchedEffect(hasEnabledReminders, permissionHealth.notificationsGranted) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            hasEnabledReminders &&
            !permissionHealth.notificationsGranted &&
            !notificationPermissionRequested
        ) {
            notificationPermissionRequested = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(hasEnabledStrongReminders, permissionHealth.fullScreenIntentGranted) {
        if (
            hasEnabledStrongReminders &&
            !permissionHealth.fullScreenIntentGranted &&
            !fullScreenGuidanceShown
        ) {
            fullScreenGuidanceShown = true
            val result = snackbarHostState.showSnackbar(
                message = "已启用强提醒；未开启全屏能力时会降级为锁屏抬头通知",
                actionLabel = "去设置",
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.selectSection(AppSection.SETTINGS)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (secondarySection) state.selectedSection.label else "乐醒") },
                navigationIcon = {
                    if (secondarySection) {
                        IconButton(onClick = { viewModel.selectSection(AppSection.SETTINGS) }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回设置中心")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                navigationItems.forEach { item ->
                    NavigationBarItem(
                        selected = state.selectedSection == item.section ||
                            (secondarySection && item.section == AppSection.SETTINGS),
                        onClick = { viewModel.selectSection(item.section) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.section.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (state.selectedSection == AppSection.REMINDERS) {
                FloatingActionButton(onClick = viewModel::createEvent) {
                    Icon(Icons.Outlined.AddAlarm, contentDescription = "新建提醒")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            val screen: @Composable (AppSection) -> Unit = { section -> when (section) {
            AppSection.CALENDAR -> CalendarScreen(toolsState.days, toolsState.upcoming, toolsState.busy, toolsViewModel::setCalendar, Modifier.padding(padding))
            AppSection.DATA -> DataScreen(toolsState,
                onExport = { onlySettings -> recoveryExport = false; settingsExport = onlySettings; exportPicker.launch("乐醒-${if (onlySettings) "设置" else "数据"}-${java.time.LocalDate.now()}.json") },
                onCsv = { csvPicker.launch("乐醒-统计-${java.time.LocalDate.now()}.csv") },
                onImport = { importPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                onMerge = { toolsViewModel.mergeImport(it, false) }, onSettings = toolsViewModel::applySettings,
                onDismiss = toolsViewModel::dismissImport,
                onRecovery = { recoveryExport = true; exportPicker.launch("乐醒-导入前快照-${java.time.LocalDate.now()}.json") }, modifier = Modifier.padding(padding))
            AppSection.AI -> AiScreen(toolsState, toolsViewModel::saveAi, toolsViewModel::clearAiKey,
                toolsViewModel::prepareAi, toolsViewModel::sendAi, toolsViewModel::dismissAiRequest, toolsViewModel::cancelAi,
                onMerge = { toolsViewModel.mergeImport(it, true) }, onDismissDraft = toolsViewModel::dismissAiDraft,
                    onSelectProfile = toolsViewModel::selectAiProfile, onScopeChange = toolsViewModel::setAiScope, onTrackScopeChange = toolsViewModel::setAiTrackScope,
                    onApplyEdit = toolsViewModel::applyAiEdit, onUndo = toolsViewModel::undoAiAction, modifier = Modifier.padding(padding))
            AppSection.STATISTICS -> StatisticsScreen(
                daily = state.dailyUsage, history = state.ringHistory, enabled = state.settings.usageStatsEnabled,
                onEnabled = viewModel::setUsageStatsEnabled, modifier = Modifier.padding(padding),
            )
            AppSection.HISTORY -> RingHistoryScreen(
                entries = state.ringHistory,
                modifier = Modifier.padding(padding),
            )
            AppSection.REMINDERS -> ReminderScreen(
                events = state.events,
                onAdd = viewModel::createEvent,
                onEdit = viewModel::editEvent,
                onToggle = viewModel::setEventEnabled,
                onTest = viewModel::testReminder, onAwake = viewModel::acknowledgeWindow,onComplete={viewModel.openTaskCompletion(it)},
                modifier = Modifier.padding(padding),
            )

            AppSection.MUSIC -> MusicScreen(
                tracks = state.tracks,
                previewingTrackId = state.previewingTrackId,
                isImporting = state.isImporting,
                onImport = { musicPicker.launch(arrayOf("audio/*")) },
                onPreview = viewModel::previewMusic,
                onStopPreview = viewModel::stopPreview,
                onToggle = viewModel::setMusicEnabled,
                onDelete = viewModel::deleteMusic,
                previewOptions = state.settings.previewOptions,
                onPreviewOptions = viewModel::setPreviewOptions,
                onBulkToggle = viewModel::setMusicEnabledBulk,
                importProgress = state.importProgress,
                onDismissImport = viewModel::dismissImportProgress,
                modifier = Modifier.padding(padding),
            )

            AppSection.COMPLETIONS -> CompletionHistoryScreen(state.taskRecords,state.events.mapTo(hashSetOf()) { it.id },state.organizerBusy,viewModel::reopenTask,{taskCsvPicker.launch("乐醒-完成记录-${java.time.LocalDate.now()}.csv")},Modifier.padding(padding))
            AppSection.TAGS -> TagsScreen(state.events,state.deletedEvents,state.organizerBusy,viewModel::renameTag,viewModel::removeTag,Modifier.padding(padding))
            AppSection.TRASH -> TrashScreen(state.deletedEvents,state.organizerBusy,viewModel::restoreEvent,viewModel::permanentlyDeleteEvents,Modifier.padding(padding))
            AppSection.SETTINGS -> SettingsHubScreen(state.settings, viewModel::selectSection, Modifier.padding(padding),trashCount=state.deletedEvents.size)
            AppSection.APPEARANCE -> AppearanceScreen(state.settings.appearance, viewModel::setAppearance, Modifier.padding(padding))
            AppSection.REMINDER_SETTINGS -> SettingsScreen(
                settings = state.settings,
                permissionHealth = permissionHealth.copy(
                    fullScreenIntentRequired = hasEnabledStrongReminders,
                ),
                onGentleDurationChange = viewModel::setGentleDuration,
                onStrongDurationChange = viewModel::setStrongDuration,
                onDefaultSnoozeChange = viewModel::setDefaultSnooze,
                onNotifyMissedChange = viewModel::setNotifyMissed,
                onRequestNotifications = {
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !notificationPermissionRequested
                    ) {
                        notificationPermissionRequested = true
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings(context)
                    }
                },
                onRequestExactAlarm = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        openSettings(
                            context,
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            includePackageUri = true,
                        )
                    }
                },
                onRequestFullScreenIntent = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        openSettings(
                            context,
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            includePackageUri = true,
                        )
                    }
                },
                onOpenBackgroundSettings = {
                    openSettings(context, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, includePackageUri = true)
                },
                modifier = Modifier.padding(padding),
            )
            } }
            screenStates.SaveableStateProvider(state.selectedSection.name) {
                Box(Modifier.fillMaxSize().testTag("page_${state.selectedSection.name}")) { screen(state.selectedSection) }
            }
        }
    }

    state.completionChoices?.let { data -> TaskCompletionDialog(data,state.organizerBusy,
        {range -> viewModel.openTaskCompletion(data.eventId,range)},viewModel::completeTask,viewModel::dismissTaskCompletion) }
    state.editingEvent?.let { draft ->
        EventEditorDialog(
            initial = draft,
            tracks = state.tracks,
            gentleDefaultSeconds = state.settings.gentleDurationSeconds,
            strongDefaultSeconds = state.settings.strongDurationSeconds,
            onDraftChange = viewModel::updateEventDraft,
            onDismiss = viewModel::closeEditor,
            onSave = viewModel::saveEvent,
            onDelete = draft.id?.let { { id -> viewModel.deleteEvent(id) } },
            onPreview = viewModel::previewSchedule,
        )
    }
}

private fun readPermissionHealth(context: Context): PermissionHealthUi {
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    val reminderChannelsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        listOf(
            ReminderNotificationFactory.CHANNEL_GENTLE,
            ReminderNotificationFactory.CHANNEL_STRONG,
        ).all { channelId ->
            val channel = notificationManager.getNotificationChannel(channelId)
            channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        }
    } else {
        true
    }
    val notificationsGranted = reminderChannelsEnabled &&
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED)

    val exactAlarmGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    } else {
        true
    }

    val fullScreenIntentGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        notificationManager.canUseFullScreenIntent()
    } else {
        true
    }

    return PermissionHealthUi(
        notificationsGranted = notificationsGranted,
        exactAlarmGranted = exactAlarmGranted,
        fullScreenIntentGranted = fullScreenIntentGranted,
        batteryOptimizationIgnored = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName),
        backgroundRestricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.getSystemService(ActivityManager::class.java).isBackgroundRestricted,
    )
}

private fun openSettings(
    context: Context,
    action: String,
    includePackageUri: Boolean,
) {
    val intent = Intent(action).apply {
        if (includePackageUri) data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            openSettings(
                context = context,
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                includePackageUri = true,
            )
        }
}
