package com.muyex.randommusicreminder.playback

import android.content.Context
import android.net.Uri
import android.content.ComponentName
import android.os.SystemClock
import android.os.PowerManager
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.muyex.randommusicreminder.alarm.AlarmRequest
import com.muyex.randommusicreminder.alarm.AlarmScheduleMode
import com.muyex.randommusicreminder.alarm.AndroidAlarmScheduler
import com.muyex.randommusicreminder.alarm.ReminderIntensity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.concurrent.atomic.AtomicReference
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmPlaybackIntegrationTest {
    private lateinit var testWakeLock: PowerManager.WakeLock
    private var visibleTestScreen: ActivityScenario<RingActivity>? = null

    @Before
    fun keepTestTimeoutsRunningWhileScreenIsOff() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        testWakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lexing:test-timeouts")
        // 只保证测试线程的短超时可执行，不改变系统休眠或省电配置。
        testWakeLock.acquire(30_000L)
    }

    private fun showVisibleTestScreen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // 厂商可冻结后台测试进程；前台功能测试使用自家锁屏页，不代表长时间息屏通过。
        visibleTestScreen = ActivityScenario.launch(
            android.content.Intent(context, RingActivity::class.java).apply {
                putExtra(PlaybackExtras.OCCURRENCE_ID, "silent-test-screen")
                putExtra(PlaybackExtras.TITLE, "乐醒 · 无声功能验证")
                putExtra(PlaybackExtras.SNOOZE_ENABLED, false)
            },
        )
    }

    @After
    fun releaseTestWakeLock() {
        if (::testWakeLock.isInitialized && testWakeLock.isHeld) testWakeLock.release()
        visibleTestScreen?.close()
    }

    @Test
    fun exactAlarmFlowsThroughReceiverAndPlaybackService() {
        showVisibleTestScreen()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalDependencies = PlaybackDependencyRegistry.current()
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val testOccurrenceId = "alarm-playback-integration"
        val silentFile = createSilentWav(context)
        val fakeDependencies = object : PlaybackServiceDependencies {
            override suspend fun loadOccurrence(occurrenceId: String): PlaybackOccurrenceInfo? {
                if (occurrenceId != testOccurrenceId) return null
                return PlaybackOccurrenceInfo(
                    occurrenceId = testOccurrenceId,
                    eventId = "1",
                    title = "调度播放集成测试",
                    intensity = ReminderIntensity.GENTLE,
                    ringDurationMs = 30_000,
                )
            }

            override suspend fun selectNextTrack(eventId: String): PlaybackTrackInfo = PlaybackTrackInfo(
                id = "silent-test-track",
                uri = Uri.fromFile(silentFile).toString(),
                displayName = "无声自动化测试",
            )

            override suspend fun onPlaybackStarted(occurrenceId: String, trackId: String?): Boolean {
                started.countDown()
                return true
            }

            override suspend fun onTrackFailed(
                occurrenceId: String,
                trackId: String?,
                message: String,
            ) = Unit

            override suspend fun onPlaybackStopped(
                occurrenceId: String,
                reason: PlaybackStopReason,
            ) {
                stopped.countDown()
            }

            override suspend fun createSnooze(
                occurrenceId: String,
                minutes: Int,
            ): AlarmRequest? = null
        }

        PlaybackDependencyRegistry.install(fakeDependencies)
        val scheduler = AndroidAlarmScheduler(context)
        try {
            val request = AlarmRequest(
                occurrenceId = testOccurrenceId,
                eventId = "1",
                title = "调度播放集成测试",
                triggerAtEpochMs = System.currentTimeMillis() + 1_000,
                ringDurationMs = 30_000,
                intensity = ReminderIntensity.GENTLE,
            )
            assertEquals(AlarmScheduleMode.EXACT, scheduler.schedule(request).mode)
            assertTrue("精确闹钟未启动播放服务", started.await(10, TimeUnit.SECONDS))

            context.startService(
                ReminderPlaybackService.actionIntent(
                    context,
                    ReminderPlaybackService.ACTION_STOP,
                    testOccurrenceId,
                ),
            )
            assertTrue("停止操作未完成提醒会话", stopped.await(5, TimeUnit.SECONDS))
        } finally {
            scheduler.cancel(testOccurrenceId)
            context.stopService(android.content.Intent(context, ReminderPlaybackService::class.java))
            if (originalDependencies == null) {
                PlaybackDependencyRegistry.clear()
            } else {
                PlaybackDependencyRegistry.install(originalDependencies)
            }
        }
    }

    @Test
    fun stopActionAfterServiceRecreationCompletesOccurrence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = PlaybackDependencyRegistry.current()
        val stopped = CountDownLatch(1)
        val occurrenceId = "cold-stop-integration"
        context.stopService(android.content.Intent(context, ReminderPlaybackService::class.java))
        PlaybackDependencyRegistry.install(object : PlaybackServiceDependencies {
            override suspend fun loadOccurrence(occurrenceId: String): PlaybackOccurrenceInfo? = null
            override suspend fun selectNextTrack(eventId: String): PlaybackTrackInfo? = null
            override suspend fun onPlaybackStarted(occurrenceId: String, trackId: String?) = false
            override suspend fun onTrackFailed(occurrenceId: String, trackId: String?, message: String) = Unit
            override suspend fun createSnooze(occurrenceId: String, minutes: Int): AlarmRequest? = null
            override suspend fun onPlaybackStopped(occurrenceId: String, reason: PlaybackStopReason) {
                if (occurrenceId == "cold-stop-integration" && reason == PlaybackStopReason.USER_STOPPED) {
                    stopped.countDown()
                }
            }
        })
        try {
            context.startService(
                ReminderPlaybackService.actionIntent(context, ReminderPlaybackService.ACTION_STOP, occurrenceId),
            )
            assertTrue("服务重建后的停止操作未处理提醒", stopped.await(5, TimeUnit.SECONDS))
        } finally {
            context.stopService(android.content.Intent(context, ReminderPlaybackService::class.java))
            if (original != null) PlaybackDependencyRegistry.install(original) else PlaybackDependencyRegistry.clear()
        }
    }

    @Test
    fun testSessionCapsVolumeAndAutomaticallyStopsWithinTwoSeconds() {
        showVisibleTestScreen()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = PlaybackDependencyRegistry.current()
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val silentFile = createSilentWav(context)
        val testId = "short-test-session"
        val actualReason = AtomicReference<PlaybackStopReason>()
        PlaybackDependencyRegistry.install(object : PlaybackServiceDependencies {
            override suspend fun loadOccurrence(occurrenceId: String) = PlaybackOccurrenceInfo(
                testId, "1", "低音量时限测试", ReminderIntensity.GENTLE, 30_000,
                snoozeMinutes = null, isTest = true,
            )
            override suspend fun selectNextTrack(eventId: String) = PlaybackTrackInfo(
                "silent", Uri.fromFile(silentFile).toString(), "静音测试音频",
            )
            override suspend fun onPlaybackStarted(occurrenceId: String, trackId: String?): Boolean {
                started.countDown()
                return true
            }
            override suspend fun onTrackFailed(occurrenceId: String, trackId: String?, message: String) = Unit
            override suspend fun createSnooze(occurrenceId: String, minutes: Int): AlarmRequest? = null
            override suspend fun onPlaybackStopped(occurrenceId: String, reason: PlaybackStopReason) {
                actualReason.set(reason)
                stopped.countDown()
            }
        })
        val scheduler = AndroidAlarmScheduler(context)
        var controller: MediaController? = null
        try {
            scheduler.schedule(
                AlarmRequest(testId, "1", "无声时限测试", System.currentTimeMillis() + 1_000, 30_000, ReminderIntensity.GENTLE),
            )
            assertTrue(started.await(10, TimeUnit.SECONDS))
            val startedAt = SystemClock.elapsedRealtime()
            val future = MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, ReminderPlaybackService::class.java)),
            ).buildAsync()
            val connectedController = future.get(3, TimeUnit.SECONDS)
            controller = connectedController
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertTrue("试响播放器音量超过 5%", connectedController.volume <= 0.05f)
            }
            assertTrue("试响未自动停止", stopped.await(4, TimeUnit.SECONDS))
            assertTrue("试响超过时限", SystemClock.elapsedRealtime() - startedAt < 3_500)
            assertEquals(PlaybackStopReason.DURATION_REACHED, actualReason.get())
        } finally {
            controller?.let { sessionController ->
                InstrumentationRegistry.getInstrumentation().runOnMainSync { sessionController.release() }
            }
            scheduler.cancel(testId)
            context.stopService(android.content.Intent(context, ReminderPlaybackService::class.java))
            if (original != null) PlaybackDependencyRegistry.install(original) else PlaybackDependencyRegistry.clear()
        }
    }

    private fun createSilentWav(context: Context): File {
        val dataSize = 8_000
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
        buffer.putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
        buffer.putShort(2).putShort(16).put("data".toByteArray()).putInt(dataSize)
        return File.createTempFile("silent-device-test-", ".wav", context.cacheDir).apply {
            writeBytes(buffer.array())
        }
    }
}
