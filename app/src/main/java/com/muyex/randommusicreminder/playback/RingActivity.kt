package com.muyex.randommusicreminder.playback

import android.content.Intent
import com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.OutlinedButton
import com.muyex.randommusicreminder.ui.components.TaskCompletionDialog
import com.muyex.randommusicreminder.domain.tasks.TaskChoiceList
import kotlinx.coroutines.launch
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muyex.randommusicreminder.app.LexingApplication
import com.muyex.randommusicreminder.data.settings.AppSettings
import com.muyex.randommusicreminder.ui.theme.LexingTheme
import com.muyex.randommusicreminder.ui.components.MusicArtwork
import com.muyex.randommusicreminder.ui.components.LyricsPanel
import com.muyex.randommusicreminder.ui.components.rememberEmbeddedMetadata
import androidx.core.content.ContextCompat
import com.muyex.randommusicreminder.alarm.ReminderIntensity

class RingActivity : ComponentActivity() {
    private var screenState by mutableStateOf(RingScreenState())
    private val finishedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra(PlaybackExtras.OCCURRENCE_ID) == screenState.occurrenceId) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this,
            finishedReceiver,
            IntentFilter(ReminderPlaybackService.ACTION_FINISHED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
        )
        screenState = intent.toScreenState()
        if (screenState.occurrenceId.isBlank()) {
            finish()
            return
        }
        retryPlaybackIfRequested(intent)

        setContent {
            val settings by (application as LexingApplication).container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            com.muyex.randommusicreminder.ui.theme.SystemBarAppearance(this, settings.appearance)
            LexingTheme(settings.appearance) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RingScreen(
                        state = screenState,
                        onStop = { stopReminder() },
                        onSnooze = { minutes -> snooze(minutes) },
                        onAwake = { startService(ReminderPlaybackService.actionIntent(this, ReminderPlaybackService.ACTION_AWAKE, screenState.occurrenceId)) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        screenState = intent.toScreenState()
        retryPlaybackIfRequested(intent)
    }

    override fun onDestroy() {
        unregisterReceiver(finishedReceiver)
        super.onDestroy()
    }

    private fun stopReminder() {
        startService(
            ReminderPlaybackService.actionIntent(
                this,
                ReminderPlaybackService.ACTION_STOP,
                screenState.occurrenceId,
            ),
        )
        finish()
    }

    private fun snooze(minutes: Int) {
        startService(
            ReminderPlaybackService.actionIntent(
                this,
                ReminderPlaybackService.ACTION_SNOOZE,
                screenState.occurrenceId,
                minutes,
            ),
        )
        finish()
    }

    private fun Intent.toScreenState(): RingScreenState = RingScreenState(
        occurrenceId = getStringExtra(PlaybackExtras.OCCURRENCE_ID).orEmpty(),
        eventId = getStringExtra(PlaybackExtras.EVENT_ID).orEmpty(),
        title = getStringExtra(PlaybackExtras.TITLE).orEmpty(),
        outputMode=getStringExtra(PlaybackExtras.OUTPUT_MODE)?.let { raw -> ReminderOutputMode.entries.firstOrNull { it.name==raw } ?: ReminderOutputMode.NOTIFICATION } ?: ReminderOutputMode.MUSIC,
        outputSources=getStringExtra(PlaybackExtras.OUTPUT_SOURCES),outputStatus=getStringExtra(PlaybackExtras.OUTPUT_STATUS),
        hasTaskCompletion=getBooleanExtra(PlaybackExtras.TASK_COMPLETION_ENABLED,false),
        timingLabel = getStringExtra(PlaybackExtras.TIMING_LABEL), hasWindowBackup = getBooleanExtra(PlaybackExtras.WINDOW_BACKUP, false),
        intensity = ReminderIntensity.from(getStringExtra(PlaybackExtras.INTENSITY)),
        ringDurationMs = getLongExtra(PlaybackExtras.RING_DURATION, 60_000L),
        snoozeMinutes = if (getBooleanExtra(PlaybackExtras.SNOOZE_ENABLED, true)) {
            getIntExtra(PlaybackExtras.SNOOZE_MINUTES, 10)
        } else {
            null
        },
    )

    private fun retryPlaybackIfRequested(intent: Intent) {
        if (!intent.getBooleanExtra(PlaybackExtras.RETRY_START, false)) return
        intent.removeExtra(PlaybackExtras.RETRY_START)
        val request = PlaybackStartRequest(
            occurrenceId = screenState.occurrenceId,
            eventId = screenState.eventId,
            title = screenState.title,
            intensity = screenState.intensity,
            ringDurationMs = screenState.ringDurationMs,
            outputMode = screenState.outputMode,
        )
        // Activity 已可见，此时可安全重试被后台启动限制拦截的前台播放服务。
        ContextCompat.startForegroundService(this, ReminderPlaybackService.startIntent(this, request))
    }
}

internal data class RingScreenState(
    val occurrenceId: String = "",
    val eventId: String = "",
    val title: String = "",
    val intensity: ReminderIntensity = ReminderIntensity.GENTLE,
    val ringDurationMs: Long = 60_000L,
    val snoozeMinutes: Int? = 10,
    val timingLabel: String? = null,
    val hasWindowBackup: Boolean = false,
    val outputMode: ReminderOutputMode = ReminderOutputMode.MUSIC,
    val outputSources: String? = null,val outputStatus: String? = null,
    val hasTaskCompletion:Boolean=false,
)

@Composable
private fun RingScreen(state: RingScreenState, onStop: () -> Unit, onSnooze: (Int) -> Unit, onAwake: () -> Unit) {
    val context = LocalContext.current
    var hasBackup by androidx.compose.runtime.remember(state.occurrenceId) { mutableStateOf(state.hasWindowBackup) }
    LaunchedEffect(state.occurrenceId) {
        val repository = (context.applicationContext as LexingApplication).container.eventRepository
        val occurrence = repository.getScheduledOccurrence(state.occurrenceId)
        hasBackup = occurrence?.selectedLocalMinute != null && repository.getEvent(occurrence.eventId)?.event?.windowBackupEnabled == true
    }
    BackHandler(enabled = true) { }
    val current by PlaybackVisualState.current.collectAsStateWithLifecycle()
    val visual = current.takeIf { it.occurrenceId == state.occurrenceId }
    val metadata = rememberEmbeddedMetadata(visual?.uri.orEmpty())
    val updated=visual?.occurrence
    val actual=if(updated==null) state else state.copy(title=updated.title,intensity=updated.intensity,snoozeMinutes=updated.snoozeMinutes,
        timingLabel=updated.timingLabel,hasWindowBackup=updated.hasWindowBackup,outputMode=updated.outputMode,
        outputSources=updated.outputSources,outputStatus=updated.outputStatus,hasTaskCompletion=updated.hasTaskCompletion)
    val scope=rememberCoroutineScope()
    var choices by remember(state.occurrenceId) { mutableStateOf<TaskChoiceList?>(null) }
    var completing by remember { mutableStateOf(false) }
    val tasks=(context.applicationContext as LexingApplication).container.taskRepository
    RingContent(actual, metadata, visual?.name ?: if(actual.outputMode.audio) "正在载入歌曲…" else actual.outputMode.label, visual?.positionMillis, onStop, onSnooze, if (updated?.hasWindowBackup ?: hasBackup) onAwake else null,
        if(actual.hasTaskCompletion) ({scope.launch { runCatching { tasks.sessionChoices(state.occurrenceId) }.onSuccess { choices=it }
            .onFailure { android.widget.Toast.makeText(context,it.message ?: "完成范围载入失败，请重试",android.widget.Toast.LENGTH_SHORT).show() } } ; Unit}) else null)
    choices?.let { data -> TaskCompletionDialog(data,completing,{}, { choice ->
        completing=true
        scope.launch {
            try {
                val result=tasks.complete(choice);choices=null
                android.widget.Toast.makeText(context,if(result.rescheduled) "本次事项已完成，其他安排保留" else "完成已保存，系统重排失败，请重新打开应用核对",android.widget.Toast.LENGTH_SHORT).show()
            } catch(error: Exception) { android.widget.Toast.makeText(context,error.message ?: "完成未保存，请重试",android.widget.Toast.LENGTH_SHORT).show() }
            finally { completing=false }
        }
    },{choices=null}) }

}

@Composable
internal fun RingContent(state: RingScreenState, metadata: com.muyex.randommusicreminder.music.EmbeddedMusicMetadata,
    trackName: String, positionMillis: Long?, onStop: () -> Unit, onSnooze: (Int) -> Unit, onAwake: (() -> Unit)? = null,onComplete:(()->Unit)?=null) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (state.intensity == ReminderIntensity.STRONG) "强提醒" else "温和提醒", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        state.timingLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        Text(state.outputMode.label,style=MaterialTheme.typography.titleMedium)
        state.outputSources?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
        state.outputStatus?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
        Text(state.title.ifBlank { "提醒时间到了" }, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 3)
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if(state.outputMode.audio) {
            MusicArtwork(metadata, Modifier.size(160.dp))
            Text(metadata.title.ifBlank { trackName }, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            if (metadata.artist.isNotBlank()) Text(metadata.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LyricsPanel(metadata.lyrics, positionMillis, Modifier.weight(1f).fillMaxWidth())
            } else Text(if(state.outputMode.vibration) "仅请求震动，不播放音乐。触感受设备和系统设置影响。" else "已显示无声通知，不播放音乐或震动。",style=MaterialTheme.typography.bodyLarge)
        }
        if (onAwake != null) {
            Button(onClick = onAwake, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("已收到 · 结束并取消本次补充") }
            Text("仅停止声音会保留窗口末补充提醒", style = MaterialTheme.typography.bodySmall)
        }
        if(onComplete!=null) OutlinedButton(onClick=onComplete,modifier=Modifier.fillMaxWidth().height(48.dp)) { Text("完成本次事项…") }
        Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("停止提醒") }
        if (state.snoozeMinutes != null) {
            Text("稍后提醒 · 默认 ${state.snoozeMinutes} 分钟", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(5, 10, 15).forEach { minutes -> OutlinedButton(onClick = { onSnooze(minutes) }, modifier = Modifier.weight(1f)) { Text("${minutes}分钟") } }
            }
        }
    }
}
