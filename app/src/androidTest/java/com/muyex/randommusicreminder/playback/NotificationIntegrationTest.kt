package com.muyex.randommusicreminder.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.muyex.randommusicreminder.alarm.ReminderIntensity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationIntegrationTest {
    @Test
    fun foregroundNotificationContainsStopAndThreeSnoozeActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = ReminderNotificationFactory(context).buildForeground(
            PlaybackOccurrenceInfo(
                occurrenceId = "integration-occurrence",
                eventId = "1",
                title = "集成测试提醒",
                intensity = ReminderIntensity.GENTLE,
                ringDurationMs = 30_000,
            ),
            trackName = "测试音乐",
        )

        assertNotNull(notification.contentIntent)
        assertEquals(4, notification.actions.size)
    }

    @Test
    fun strongNotificationKeepsFullScreenFallbackIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = ReminderNotificationFactory(context).buildForeground(
            PlaybackOccurrenceInfo(
                occurrenceId = "strong-integration-occurrence",
                eventId = "2",
                title = "强提醒集成测试",
                intensity = ReminderIntensity.STRONG,
                ringDurationMs = 60_000,
            ),
            trackName = null,
        )

        // Android 14+ 无全屏资格时由系统把这个入口降级为锁屏抬头通知。
        assertNotNull(notification.fullScreenIntent)
    }

    @Test
    fun disabledSnoozeOnlyShowsStopAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = ReminderNotificationFactory(context).buildForeground(
            PlaybackOccurrenceInfo("no-snooze", "1", "不允许稍后", ReminderIntensity.GENTLE, 30_000, null),
            null,
        )
        assertEquals(1, notification.actions.size)
        assertEquals("停止", notification.actions[0].title.toString())
    }

    @Test
    fun configuredSnoozeComesFirstInNotification() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = ReminderNotificationFactory(context).buildForeground(
            PlaybackOccurrenceInfo("prefer-fifteen", "1", "默认稍后", ReminderIntensity.GENTLE, 30_000, 15),
            null,
        )
        assertEquals("稍后 15 分钟", notification.actions[1].title.toString())
    }

    @Test
    fun builtInFallbackToneCreatesValidWavFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = requireNotNull(FallbackToneProvider.uri(context))
        val file = File(requireNotNull(uri.path))
        val signature = file.inputStream().use { input ->
            ByteArray(4).also { bytes -> assertEquals(4, input.read(bytes)) }
        }

        assertTrue(file.length() > 44)
        assertEquals("RIFF", signature.toString(Charsets.US_ASCII))
    }
}
