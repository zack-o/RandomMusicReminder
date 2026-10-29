package com.muyex.randommusicreminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import com.muyex.randommusicreminder.app.MainActivity

class AndroidAlarmScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun schedule(request: AlarmRequest): AlarmScheduleResult {
        require(request.occurrenceId.isNotBlank()) { "occurrenceId 不能为空" }
        require(request.eventId.isNotBlank()) { "eventId 不能为空" }
        require(request.ringDurationMs > 0L) { "响铃时长必须大于 0" }

        val pendingIntent = pendingIntent(request, PendingIntent.FLAG_UPDATE_CURRENT)
        val triggerAt = request.triggerAtEpochMs.coerceAtLeast(System.currentTimeMillis() + 100L)
        val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        if (canUseExact) {
            try {
                if (request.intensity == ReminderIntensity.STRONG) {
                    // 强提醒登记为系统闹钟类型，温和提醒仍保持普通精确提醒语义。
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerAt, showReminderPendingIntent(request)),
                        pendingIntent,
                    )
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent,
                    )
                }
                return AlarmScheduleResult(AlarmScheduleMode.EXACT, triggerAt)
            } catch (_: SecurityException) {
                // 某些厂商系统可能在权限状态更新前仍拒绝精确闹钟，继续使用可靠的非精确降级。
            }
        }

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent,
        )
        return AlarmScheduleResult(AlarmScheduleMode.INEXACT_FALLBACK, triggerAt)
    }

    fun cancel(occurrenceId: String) {
        if (occurrenceId.isBlank()) return
        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = AlarmIntentContract.ACTION_FIRE
            data = AlarmIntentContract.dataUri(occurrenceId)
        }
        val existing = PendingIntent.getBroadcast(
            appContext,
            occurrenceId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(existing)
        existing.cancel()
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun scheduleMaintenance(triggerAtEpochMs: Long) {
        val intent = Intent(appContext, SystemEventReceiver::class.java).apply {
            action = SystemEventReceiver.ACTION_REFRESH_HORIZON
            data = Uri.parse("lexing://maintenance/schedule-horizon")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            MAINTENANCE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtEpochMs.coerceAtLeast(System.currentTimeMillis() + 60_000L),
            pendingIntent,
        )
    }

    private fun pendingIntent(request: AlarmRequest, updateFlag: Int): PendingIntent {
        val intent = AlarmIntentContract.putRequest(
            Intent(appContext, AlarmReceiver::class.java),
            request,
        )
        return PendingIntent.getBroadcast(
            appContext,
            request.occurrenceId.hashCode(),
            intent,
            updateFlag or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showReminderPendingIntent(request: AlarmRequest): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            data = Uri.Builder()
                .scheme("lexing")
                .authority("alarm-info")
                .appendPath(request.eventId)
                .build()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            appContext,
            request.eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val MAINTENANCE_REQUEST_CODE = 0x4C58
    }
}
