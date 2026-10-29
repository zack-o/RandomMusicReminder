package com.muyex.randommusicreminder.playback

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.PowerManager
import android.content.pm.ServiceInfo
import com.muyex.randommusicreminder.domain.haptics.*
import com.muyex.randommusicreminder.music.HapticPatternStore
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.muyex.randommusicreminder.alarm.AlarmRequest
import com.muyex.randommusicreminder.alarm.AndroidAlarmScheduler
import com.muyex.randommusicreminder.alarm.ReminderIntensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class ReminderPlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commandMutex = Mutex()
    private var audioPlayer: ExoPlayer? = null
    private val player: ExoPlayer get()=checkNotNull(audioPlayer)
    private var mediaSession: MediaSession? = null
    private val hapticOutputHolder=lazy { ReminderHaptics.create(this,alarm=true) }
    private val hapticOutput get()=hapticOutputHolder.value
    private lateinit var notificationFactory: ReminderNotificationFactory
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private lateinit var alarmScheduler: AndroidAlarmScheduler
    private var activePlayback: ActivePlayback? = null
    private var stopTimer: Job? = null
    private var visualTicker: Job? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false
    private var outputWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        notificationFactory = ReminderNotificationFactory(this)
        notificationManager = getSystemService(NotificationManager::class.java)
        audioManager = getSystemService(AudioManager::class.java)
        alarmScheduler = AndroidAlarmScheduler(this)
        serviceScope.launch { TaskPlaybackChanges.changes.collect {
            commandMutex.withLock {
                try { refreshAfterTaskChange() }
                catch(_: Exception) { Toast.makeText(this@ReminderPlaybackService,"完成已保存，提示刷新失败，可手动停止当前会话",Toast.LENGTH_SHORT).show() }
            }
        } }
    }

    private fun initializeAudio() {
        if(audioPlayer != null) return
        audioPlayerCreations++
        audioPlayer = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setWakeMode(C.WAKE_MODE_LOCAL)
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!isPlaying) return
                    val active = activePlayback ?: return
                    if(!active.occurrence.outputMode.audio) return
                    val musicName = active.track?.displayName ?: "兜底铃声"
                    serviceScope.launch {
                        commandMutex.withLock {
                            if (activePlayback?.occurrence?.occurrenceId == active.occurrence.occurrenceId && activePlayback?.track?.id==active.track?.id) {
                                runCatching {
                                    active.dependencies?.onMusicPlaying(active.occurrence.occurrenceId, musicName)
                                }
                            }
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    serviceScope.launch {
                        commandMutex.withLock { handlePlayerError(error) }
                    }
                }
            })
        }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val request = intent.toStartRequest() ?: return START_NOT_STICKY
                // 前台服务必须立即发布通知；仓库读取和选曲随后异步完成。
                if (activePlayback == null) {
                    try { startOutputForeground(notificationFactory.buildForeground(request.asOccurrence(),null,mediaSession),request.outputMode.audio) }
                    catch(_: RuntimeException) {
                        notificationFactory.showStartFailure(AlarmRequest(request.occurrenceId,request.eventId,request.title,System.currentTimeMillis(),request.ringDurationMs,request.intensity,request.outputMode))
                        stopSelf();return START_NOT_STICKY
                    }
                }
                serviceScope.launch {
                    commandMutex.withLock {
                        val previousOccurrenceId = activePlayback?.occurrence?.occurrenceId
                        try {
                            startReminder(request)
                        } catch (_: Throwable) {
                            val currentOccurrenceId = activePlayback?.occurrence?.occurrenceId
                            if (previousOccurrenceId == null || currentOccurrenceId != previousOccurrenceId) {
                                stopActive(PlaybackStopReason.PLAYBACK_ERROR, stopService = true)
                            }
                        }
                    }
                }
            }

            ACTION_STOP, ACTION_AWAKE -> {
                val occurrenceId = intent.getStringExtra(PlaybackExtras.OCCURRENCE_ID)
                serviceScope.launch {
                    commandMutex.withLock {
                        if (activePlayback == null || occurrenceId == null ||
                            activePlayback?.occurrence?.occurrenceId == occurrenceId
                        ) {
                            val active = activePlayback
                            if (intent.action == ACTION_AWAKE && occurrenceId != null) {
                                try { (active?.dependencies ?: PlaybackDependencyRegistry.current())?.acknowledgeWindow(occurrenceId) }
                                catch (_: Exception) { Toast.makeText(this@ReminderPlaybackService, "接收确认未保存，请重试", Toast.LENGTH_SHORT).show(); return@withLock }
                            }
                            if (active != null) {
                                stopActive(PlaybackStopReason.USER_STOPPED, stopService = true)
                            } else {
                                // 通知操作可能在系统回收服务后到达，仍要把该提醒标记为已处理。
                                occurrenceId?.let { stoppedOccurrenceId ->
                                    runCatching {
                                        PlaybackDependencyRegistry.current()?.onPlaybackStopped(
                                            stoppedOccurrenceId,
                                            PlaybackStopReason.USER_STOPPED,
                                        )
                                    }
                                }
                                stopForeground(STOP_FOREGROUND_REMOVE)
                                notificationManager.cancel(ReminderNotificationFactory.FOREGROUND_NOTIFICATION_ID)
                                stopSelf()
                            }
                        }
                    }
                }
            }

            ACTION_SNOOZE -> {
                val occurrenceId = intent.getStringExtra(PlaybackExtras.OCCURRENCE_ID)
                    ?: activePlayback?.occurrence?.occurrenceId
                    ?: return START_NOT_STICKY
                val minutes = intent.getIntExtra(PlaybackExtras.SNOOZE_MINUTES, 10)
                    .takeIf { it in ALLOWED_SNOOZE_MINUTES }
                    ?: 10
                serviceScope.launch {
                    commandMutex.withLock { snooze(occurrenceId, minutes) }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val active = activePlayback ?: return
        val notification = notificationFactory.buildForeground(
            active.occurrence,
            active.track?.displayName ?: if (active.usingSystemFallback) "乐醒兜底铃声" else null,
            mediaSession,
        )
        try {
            if (startInForegroundRequired) {
                startOutputForeground(notification,active.occurrence.outputMode.audio)
            } else {
                notificationManager.notify(
                    ReminderNotificationFactory.FOREGROUND_NOTIFICATION_ID,
                    notification,
                )
            }
        } catch (_: SecurityException) {
            // 未授予通知权限时媒体会继续按系统前台服务规则运行，不让回调导致服务崩溃。
        }
    }

    private suspend fun startReminder(request: PlaybackStartRequest) {
        val dependencies = PlaybackDependencyRegistry.current()
        val occurrence = if(dependencies==null) request.asOccurrence() else dependencies.preparePlayback(request.occurrenceId) ?: run {
            if(activePlayback==null) { stopForeground(STOP_FOREGROUND_REMOVE);stopSelf() }
            return
        }
        if (activePlayback != null) {
            stopActive(PlaybackStopReason.REPLACED_BY_NEW_REMINDER, stopService = false)
        }
        activatePlayback(occurrence,dependencies)
    }

    private suspend fun activatePlayback(occurrence:PlaybackOccurrenceInfo,dependencies:PlaybackServiceDependencies?) {
        val audioSource=occurrence.audioEventId ?: occurrence.eventId
        val vibrationSource=occurrence.vibrationEventId ?: occurrence.eventId
        val track=if(occurrence.outputMode.audio) selectTrack(dependencies,occurrence.copy(eventId=audioSource)) else null
        val hapticTrack=if(occurrence.outputMode.vibration && occurrence.haptics.useMusicRhythm) {
            if(occurrence.outputMode.audio && audioSource==vibrationSource) track else selectTrack(dependencies,occurrence.copy(eventId=vibrationSource))
        } else null
        activePlayback=ActivePlayback(occurrence,track,dependencies,occurrence.outputMode.audio && track==null,hapticTrack=hapticTrack)
        if(!occurrence.outputMode.audio) releaseAudioResources()
        publishActiveNotification()
        if(occurrence.outputMode.vibration || !occurrence.outputMode.audio) {
            outputWakeLock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"lexing:reminderOutput").apply {
                setReferenceCounted(false)
                acquire((if(occurrence.isTest) occurrence.ringDurationMs.coerceIn(500,2000) else occurrence.ringDurationMs.coerceIn(MIN_RING_DURATION_MS,MAX_RING_DURATION_MS))+5000)
            }
        }
        if(occurrence.outputMode.vibration) startHaptics()
        if(occurrence.outputMode.audio) {
            initializeAudio();configureAudio(occurrence.intensity)
            if(occurrence.isTest) player.volume=TEST_VOLUME
            player.setMediaItem(MediaItem.Builder().setMediaId(track?.id ?: SYSTEM_FALLBACK_MEDIA_ID).setUri(track?.uri?.let(Uri::parse) ?: defaultAlarmUri()).build())
            player.repeatMode=Player.REPEAT_MODE_ONE;player.prepare();player.play()
        }

        startVisualUpdates()

        publishActiveNotification()
        startStopTimer(occurrence.ringDurationMs,occurrence.continuation)
    }

    private suspend fun refreshAfterTaskChange() {
        val active=activePlayback ?: return
        when(val refresh=active.dependencies?.refreshPlayback(active.occurrence.occurrenceId) ?: PlaybackRefresh.Keep) {
            PlaybackRefresh.Keep -> Unit
            is PlaybackRefresh.Finish -> stopActive(refresh.reason,true)
            is PlaybackRefresh.Update -> {
                val next=refresh.occurrence
                val unchanged=next.outputMode==active.occurrence.outputMode && next.audioEventId==active.occurrence.audioEventId &&
                    next.vibrationEventId==active.occurrence.vibrationEventId && next.haptics==active.occurrence.haptics && next.intensity==active.occurrence.intensity
                if(unchanged) {
                    activePlayback=active.copy(occurrence=next.copy(outputStatus=active.occurrence.outputStatus))
                    startStopTimer(next.ringDurationMs,true);publishActiveNotification()
                } else {
                    clearOutputResources()
                    activePlayback=null
                    activatePlayback(next,active.dependencies)
                }
            }
        }
    }

    private suspend fun startHaptics() {
        val active=activePlayback ?: return
        if(!active.occurrence.outputMode.vibration) return
        hapticOutput.stop()
        val cached=if(active.occurrence.haptics.useMusicRhythm) active.hapticTrack?.let { HapticPatternStore(this).read(it.id,it.uri) } else null
        val pattern=cached?.pattern ?: RhythmPattern.preset(active.occurrence.haptics.preset)
        val fallback=if(active.occurrence.haptics.useMusicRhythm && cached==null) "未找到有效节奏缓存，使用${active.occurrence.haptics.preset.label}；" else ""
        val status=fallback+hapticOutput.start(pattern,active.occurrence.haptics)
        activePlayback=active.copy(occurrence=active.occurrence.copy(outputStatus=status))
    }

    private fun startOutputForeground(notification: Notification, audio: Boolean) {
        if(Build.VERSION.SDK_INT>=29) {
            val type=if(audio) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else if(Build.VERSION.SDK_INT>=34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0
            startForeground(ReminderNotificationFactory.FOREGROUND_NOTIFICATION_ID,notification,type)
        } else startForeground(ReminderNotificationFactory.FOREGROUND_NOTIFICATION_ID,notification)
    }

    private fun releaseAudioResources() {
        abandonAudioFocus();mediaSession?.release();mediaSession=null
        audioPlayer?.release();audioPlayer=null
    }

    private suspend fun selectTrack(
        dependencies: PlaybackServiceDependencies?,
        occurrence: PlaybackOccurrenceInfo,
    ): PlaybackTrackInfo? {
        if (dependencies == null) return null
        return runCatching {
            dependencies.selectNextTrack(occurrence.eventId)
                ?.takeIf { it.uri.isNotBlank() }
        }.getOrNull()
    }

    private fun startVisualUpdates() {
        visualTicker?.cancel()
        visualTicker = serviceScope.launch {
            while (true) {
                val active = activePlayback ?: break
                PlaybackVisualState.publish(NowPlayingVisual(active.occurrence.occurrenceId,
                    if(active.occurrence.outputMode.audio) active.track?.uri.orEmpty() else "",
                    if(active.occurrence.outputMode.audio) active.track?.displayName ?: "乐醒兜底铃声" else active.occurrence.outputMode.label,
                    audioPlayer?.currentPosition?.coerceAtLeast(0) ?: 0, active.occurrence.outputMode,active.occurrence.outputSources,active.occurrence.outputStatus,active.occurrence.hasTaskCompletion,active.occurrence))
                delay(250)
            }
        }
    }

    private suspend fun handlePlayerError(error: PlaybackException) {
        val active = activePlayback ?: return
        if(!active.occurrence.outputMode.audio || audioPlayer==null) return
        val failedTrackId = active.track?.id
        if (error.isPermanentTrackFailure()) {
            runCatching {
                active.dependencies?.onTrackFailed(
                    active.occurrence.occurrenceId,
                    failedTrackId,
                    error.errorCodeName,
                )
            }
        }

        if (!active.usingSystemFallback) {
            // 当前会话不重复尝试已经失败的曲目；遍历一轮后再使用系统兜底铃声。
            val failedTrackIds = active.failedTrackIds + listOfNotNull(failedTrackId)
            val selected = selectTrack(active.dependencies, active.occurrence)
            val nextTrack = selected?.takeUnless { it.id in failedTrackIds }
            val nextUri = nextTrack?.uri?.let(Uri::parse) ?: defaultAlarmUri()
            activePlayback = active.copy(
                track = nextTrack,
                usingSystemFallback = nextTrack == null,
                failedTrackIds = failedTrackIds,
                hapticTrack=if((active.occurrence.audioEventId ?: active.occurrence.eventId)==(active.occurrence.vibrationEventId ?: active.occurrence.eventId)) nextTrack else active.hapticTrack,
            )
            if(active.occurrence.outputMode.vibration) startHaptics()
            player.setMediaItem(MediaItem.fromUri(nextUri))
            player.prepare()
            player.play()
            publishActiveNotification()
        } else {
            stopActive(PlaybackStopReason.PLAYBACK_ERROR, stopService = true)
        }
    }

    private suspend fun snooze(occurrenceId: String, minutes: Int) {
        val current = activePlayback
        if (current != null && current.occurrence.occurrenceId != occurrenceId) return
        if (current != null && current.occurrence.snoozeMinutes == null) return
        val dependencies = activePlayback?.dependencies ?: PlaybackDependencyRegistry.current()
        val snoozeRequest = runCatching {
            dependencies?.createSnooze(occurrenceId, minutes)
        }.getOrNull()
        if (snoozeRequest == null) {
            Toast.makeText(this, "稍后提醒最多可使用 3 次", Toast.LENGTH_SHORT).show()
            return
        }
        val scheduled = runCatching { alarmScheduler.schedule(snoozeRequest) }.isSuccess
        if (!scheduled) {
            Toast.makeText(this, "稍后提醒安排失败，请先停止并重新设置", Toast.LENGTH_SHORT).show()
            return
        }

        val active = activePlayback
        if (active?.occurrence?.occurrenceId == occurrenceId) {
            stopActive(PlaybackStopReason.SNOOZED, stopService = true)
        } else {
            runCatching { dependencies?.onPlaybackStopped(occurrenceId, PlaybackStopReason.SNOOZED) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startStopTimer(durationMs: Long, continuation:Boolean=false) {
        stopTimer?.cancel()
        val boundedDuration = if(continuation) durationMs.coerceIn(1L,MAX_RING_DURATION_MS) else if (activePlayback?.occurrence?.isTest == true) {
            durationMs.coerceIn(500L, 2_000L)
        } else {
            durationMs.coerceIn(MIN_RING_DURATION_MS, MAX_RING_DURATION_MS)
        }
        stopTimer = serviceScope.launch {
            delay(boundedDuration)
            // 先清空引用，避免 stopActive() 把当前计时协程自身取消在清理流程中。
            stopTimer = null
            commandMutex.withLock {
                stopActive(PlaybackStopReason.DURATION_REACHED, stopService = true)
            }
        }
    }

    private fun clearOutputResources() {
        visualTicker?.cancel(); visualTicker = null
        PlaybackVisualState.clear()
        stopTimer?.cancel()
        stopTimer = null
        if(hapticOutputHolder.isInitialized()) hapticOutput.stop()
        releaseOutputWakeLock()
        runCatching { audioPlayer?.stop();audioPlayer?.clearMediaItems() }
        abandonAudioFocus()
    }

    private suspend fun stopActive(reason: PlaybackStopReason, stopService: Boolean) {
        clearOutputResources()
        val stopped = activePlayback
        activePlayback = null
        if (stopped != null) {
            runCatching {
                stopped.dependencies?.onPlaybackStopped(stopped.occurrence.occurrenceId, reason)
            }
            sendBroadcast(
                Intent(ACTION_FINISHED)
                    .setPackage(packageName)
                    .putExtra(PlaybackExtras.OCCURRENCE_ID, stopped.occurrence.occurrenceId),
            )
        }
        if (stopService) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            notificationManager.cancel(ReminderNotificationFactory.FOREGROUND_NOTIFICATION_ID)
            stopSelf()
        }
    }

    private fun publishActiveNotification() {
        val active = activePlayback ?: return
        val notification = notificationFactory.buildForeground(
            active.occurrence,
            active.track?.displayName ?: if (active.usingSystemFallback) "乐醒兜底铃声" else null,
            mediaSession,
        )
        startOutputForeground(notification,active.occurrence.outputMode.audio)
    }

    private fun configureAudio(intensity: ReminderIntensity) {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(
                    if (intensity == ReminderIntensity.STRONG) C.USAGE_ALARM
                    else C.USAGE_NOTIFICATION_EVENT,
                )
                .build(),
            false,
        )
        abandonAudioFocus()
        val platformUsage = if (intensity == ReminderIntensity.STRONG) {
            android.media.AudioAttributes.USAGE_ALARM
        } else {
            android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT
        }
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(platformUsage)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setOnAudioFocusChangeListener(::onAudioFocusChanged)
            .build()
        audioFocusRequest = request
        val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        player.volume = if (granted) 1f else DUCK_VOLUME
    }

    private fun onAudioFocusChanged(change: Int) {
        serviceScope.launch {
            commandMutex.withLock {
                if(activePlayback?.occurrence?.outputMode?.audio!=true || audioPlayer==null) return@withLock
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        player.volume = if (activePlayback?.occurrence?.isTest == true) TEST_VOLUME else 1f
                        if (resumeOnFocusGain && activePlayback != null) player.play()
                        resumeOnFocusGain = false
                    }

                    AudioManager.AUDIOFOCUS_LOSS -> {
                        resumeOnFocusGain = false
                        player.pause()
                    }

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        resumeOnFocusGain = player.isPlaying
                        player.pause()
                    }

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                        player.volume = if (activePlayback?.occurrence?.isTest == true) TEST_VOLUME else DUCK_VOLUME
                }
            }
        }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        audioFocusRequest = null
        resumeOnFocusGain = false
        audioPlayer?.volume = 1f
    }

    private fun releaseOutputWakeLock() {
        outputWakeLock?.let { if(it.isHeld) runCatching { it.release() } }
        outputWakeLock=null
    }

    private fun defaultAlarmUri(): Uri =
        FallbackToneProvider.uri(this)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI

    override fun onDestroy() {
        stopTimer?.cancel()
        activePlayback = null
        abandonAudioFocus()
        if(hapticOutputHolder.isInitialized()) hapticOutput.stop()
        releaseOutputWakeLock()
        releaseAudioResources()
        PlaybackVisualState.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private data class ActivePlayback(
        val occurrence: PlaybackOccurrenceInfo,
        val track: PlaybackTrackInfo?,
        val dependencies: PlaybackServiceDependencies?,
        val usingSystemFallback: Boolean,
        val failedTrackIds: Set<String> = emptySet(),
        val hapticTrack: PlaybackTrackInfo? = null,
    )

    private fun PlaybackException.isPermanentTrackFailure(): Boolean = errorCode in setOf(
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    )

    companion object {
        @Volatile internal var audioPlayerCreations=0
        const val ACTION_START = "com.muyex.randommusicreminder.action.START_PLAYBACK"
        const val ACTION_STOP = "com.muyex.randommusicreminder.action.STOP_PLAYBACK"
        const val ACTION_AWAKE = "com.muyex.randommusicreminder.action.AWAKE_PLAYBACK"
        const val ACTION_SNOOZE = "com.muyex.randommusicreminder.action.SNOOZE_PLAYBACK"
        const val ACTION_FINISHED = "com.muyex.randommusicreminder.action.PLAYBACK_FINISHED"

        private const val SYSTEM_FALLBACK_MEDIA_ID = "system-default-alarm"
        private const val MIN_RING_DURATION_MS = 5_000L
        private const val MAX_RING_DURATION_MS = 30 * 60_000L
        private const val DUCK_VOLUME = 0.25f
        private const val TEST_VOLUME = 0.05f
        private val ALLOWED_SNOOZE_MINUTES = setOf(5, 10, 15)

        fun startIntent(context: Context, request: AlarmRequest): Intent {
            return startIntent(context, PlaybackStartRequest.fromAlarm(request))
        }

        internal fun startIntent(context: Context, start: PlaybackStartRequest): Intent {
            return Intent(context, ReminderPlaybackService::class.java).apply {
                action = ACTION_START
                data = Uri.Builder()
                    .scheme("lexing")
                    .authority("playback")
                    .appendPath(start.occurrenceId)
                    .build()
                putExtra(PlaybackExtras.OCCURRENCE_ID, start.occurrenceId)
                putExtra(PlaybackExtras.EVENT_ID, start.eventId)
                putExtra(PlaybackExtras.TITLE, start.title)
                putExtra(PlaybackExtras.INTENSITY, start.intensity.name)
                putExtra(PlaybackExtras.OUTPUT_MODE, start.outputMode.name)
                putExtra(PlaybackExtras.RING_DURATION, start.ringDurationMs)
            }
        }

        fun actionIntent(
            context: Context,
            action: String,
            occurrenceId: String,
            snoozeMinutes: Int = 0,
        ): Intent = Intent(context, ReminderPlaybackService::class.java).apply {
            this.action = action
            putExtra(PlaybackExtras.OCCURRENCE_ID, occurrenceId)
            putExtra(PlaybackExtras.SNOOZE_MINUTES, snoozeMinutes)
        }

        private fun Intent.toStartRequest(): PlaybackStartRequest? {
            val occurrenceId = getStringExtra(PlaybackExtras.OCCURRENCE_ID) ?: return null
            val eventId = getStringExtra(PlaybackExtras.EVENT_ID) ?: return null
            return PlaybackStartRequest(
                occurrenceId = occurrenceId,
                eventId = eventId,
                title = getStringExtra(PlaybackExtras.TITLE).orEmpty(),
                outputMode = getStringExtra(PlaybackExtras.OUTPUT_MODE)?.let { raw -> ReminderOutputMode.entries.firstOrNull { it.name==raw } ?: ReminderOutputMode.NOTIFICATION } ?: ReminderOutputMode.MUSIC,
                intensity = ReminderIntensity.from(getStringExtra(PlaybackExtras.INTENSITY)),
                ringDurationMs = getLongExtra(PlaybackExtras.RING_DURATION, 60_000L),
            )
        }
    }
}
