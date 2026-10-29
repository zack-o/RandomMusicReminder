package com.muyex.randommusicreminder.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.muyex.randommusicreminder.R
import com.muyex.randommusicreminder.alarm.AlarmRequest
import com.muyex.randommusicreminder.alarm.ReminderIntensity

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class ReminderNotificationFactory(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    init {
        createChannels()
    }

    fun buildForeground(
        occurrence: PlaybackOccurrenceInfo,
        trackName: String?,
        mediaSession: MediaSession? = null,
    ): Notification {
        val strong = occurrence.intensity == ReminderIntensity.STRONG && occurrence.outputMode != com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode.NOTIFICATION
        val contentIntent = ringActivityPendingIntent(occurrence, retryPlayback = false)
        val builder = NotificationCompat.Builder(
            appContext,
            if (strong) CHANNEL_STRONG else CHANNEL_GENTLE,
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(occurrence.title.ifBlank { "提醒时间到了" })
            .setContentText(if(occurrence.outputMode.audio) trackName?.let { "正在播放：$it" } ?: "音乐提醒" else occurrence.outputMode.label)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .setCategory(if (strong) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (strong) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "停止",
                serviceActionPendingIntent(occurrence.occurrenceId, ReminderPlaybackService.ACTION_STOP, 0),
            )

        occurrence.timingLabel?.let { builder.setSubText(it) }
        val details=listOfNotNull(occurrence.outputSources,occurrence.outputStatus).joinToString("\n")
        if(occurrence.outputSources!=null) builder.setContentText("${occurrence.outputMode.label}：${occurrence.outputSources}")
        if(details.isNotBlank()) builder.setStyle(NotificationCompat.BigTextStyle().bigText("${occurrence.outputMode.label}\n$details"))
        if (occurrence.hasWindowBackup) builder.addAction(R.drawable.ic_launcher_foreground, "已收到·取消补充",
            serviceActionPendingIntent(occurrence.occurrenceId, ReminderPlaybackService.ACTION_AWAKE, 0))
        val snoozeOptions = occurrence.snoozeMinutes?.let { preferred ->
            listOf(preferred) + listOf(5, 10, 15).filterNot { it == preferred }
        }.orEmpty()
        snoozeOptions.forEach { minutes ->
            builder.addAction(
                R.drawable.ic_launcher_foreground,
                "稍后 $minutes 分钟",
                serviceActionPendingIntent(occurrence.occurrenceId, ReminderPlaybackService.ACTION_SNOOZE, minutes),
            )
        }

        mediaSession?.takeIf { occurrence.outputMode.audio }?.let { session ->
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(*(0..minOf(2, snoozeOptions.size)).toList().toIntArray()),
            )
        }

        if (strong) {
            // Android 14+ 无全屏资格时，系统会自动降级为锁屏抬头通知，不能静默丢弃入口。
            builder.setFullScreenIntent(contentIntent, true)
        }
        return builder.build()
    }

    fun showStartFailure(request: AlarmRequest) {
        val occurrence = PlaybackStartRequest.fromAlarm(request).asOccurrence()
        val strong = occurrence.intensity == ReminderIntensity.STRONG && occurrence.outputMode != com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode.NOTIFICATION
        val contentIntent = ringActivityPendingIntent(occurrence, retryPlayback = true)
        val builder = NotificationCompat.Builder(
            appContext,
            if (strong) CHANNEL_STRONG else CHANNEL_GENTLE,
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(occurrence.title.ifBlank { "提醒时间到了" })
            .setContentText("${occurrence.outputMode.label}未能在后台启动，点击查看")
            .setSilent(true)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(if (strong) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (strong) builder.setFullScreenIntent(contentIntent, true)
        try {
            notificationManager.notify(fallbackNotificationId(request.occurrenceId), builder.build())
        } catch (_: SecurityException) {
            // Android 13+ 未授予通知权限时系统会拒绝展示；这里不能让 Receiver 因此崩溃。
        }
    }

    private fun ringActivityPendingIntent(
        occurrence: PlaybackOccurrenceInfo,
        retryPlayback: Boolean,
    ): PendingIntent {
        val intent = Intent(appContext, RingActivity::class.java).apply {
            data = Uri.Builder()
                .scheme("lexing")
                .authority("ring")
                .appendPath(occurrence.occurrenceId)
                .build()
            putExtra(PlaybackExtras.OCCURRENCE_ID, occurrence.occurrenceId)
            putExtra(PlaybackExtras.EVENT_ID, occurrence.eventId)
            putExtra(PlaybackExtras.TITLE, occurrence.title)
            putExtra(PlaybackExtras.OUTPUT_MODE, occurrence.outputMode.name)
            putExtra(PlaybackExtras.OUTPUT_SOURCES, occurrence.outputSources)
            putExtra(PlaybackExtras.OUTPUT_STATUS, occurrence.outputStatus)
            putExtra(PlaybackExtras.TASK_COMPLETION_ENABLED,occurrence.hasTaskCompletion)
            putExtra(PlaybackExtras.INTENSITY, occurrence.intensity.name)
            putExtra(PlaybackExtras.RING_DURATION, occurrence.ringDurationMs)
            putExtra(PlaybackExtras.RETRY_START, retryPlayback)
            putExtra(PlaybackExtras.TIMING_LABEL, occurrence.timingLabel)
            putExtra(PlaybackExtras.WINDOW_BACKUP, occurrence.hasWindowBackup)
            putExtra(PlaybackExtras.SNOOZE_ENABLED, occurrence.snoozeMinutes != null)
            putExtra(PlaybackExtras.SNOOZE_MINUTES, occurrence.snoozeMinutes ?: 10)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            appContext,
            occurrence.occurrenceId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun serviceActionPendingIntent(
        occurrenceId: String,
        action: String,
        snoozeMinutes: Int,
    ): PendingIntent {
        val intent = Intent(appContext, ReminderPlaybackService::class.java).apply {
            this.action = action
            data = Uri.Builder()
                .scheme("lexing")
                .authority("playback-action")
                .appendPath(occurrenceId)
                .appendPath(action.substringAfterLast('.'))
                .appendPath(snoozeMinutes.toString())
                .build()
            putExtra(PlaybackExtras.OCCURRENCE_ID, occurrenceId)
            putExtra(PlaybackExtras.SNOOZE_MINUTES, snoozeMinutes)
        }
        return PendingIntent.getService(
            appContext,
            31 * occurrenceId.hashCode() + action.hashCode() + snoozeMinutes,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannels() {
        val silentAudio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
        val gentle = NotificationChannel(
            CHANNEL_GENTLE,
            appContext.getString(R.string.channel_gentle_name),
            notificationManager.getNotificationChannel("reminder_gentle_v1")?.importance ?: NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = appContext.getString(R.string.channel_gentle_description)
            setSound(null, silentAudio)
            enableVibration(false)
        }
        val strong = NotificationChannel(
            CHANNEL_STRONG,
            appContext.getString(R.string.channel_strong_name),
            notificationManager.getNotificationChannel("reminder_strong_v1")?.importance ?: NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = appContext.getString(R.string.channel_strong_description)
            setSound(null, silentAudio)
            enableVibration(false)
        }
        notificationManager.createNotificationChannels(listOf(gentle, strong))
    }

    private fun fallbackNotificationId(occurrenceId: String): Int =
        FALLBACK_NOTIFICATION_BASE + (occurrenceId.hashCode() and 0x0FFF)

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 41_001
        const val CHANNEL_GENTLE = "reminder_gentle_v2_silent"
        const val CHANNEL_STRONG = "reminder_strong_v2_silent"
        private const val FALLBACK_NOTIFICATION_BASE = 42_000
    }
}
