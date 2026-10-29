package com.muyex.randommusicreminder.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.muyex.randommusicreminder.alarm.*
import com.muyex.randommusicreminder.app.MainActivity
import com.muyex.randommusicreminder.domain.haptics.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class TaskPlaybackIntegrationTest {
    @Test fun completingVibrationMemberRetainsSilentNotificationMemberWithoutReclaimOrStop()=runBlocking {
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val context=ApplicationProvider.getApplicationContext<Context>()
        val previous=PlaybackDependencyRegistry.current();val previousMotor=ReminderHaptics.testFactory
        val started=CountDownLatch(1);val stopped=CountDownLatch(1);val prepares=AtomicInteger();val stops=AtomicInteger()
        val motorStops=AtomicInteger();val selections=AtomicInteger();val beforeAudio=ReminderPlaybackService.audioPlayerCreations
        val buffer=ByteBuffer.allocate(8044).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(8036).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(8000).putInt(16000).putShort(2).putShort(16).put("data".toByteArray()).putInt(8000)
        val silent=File.createTempFile("task-silence-",".wav",context.cacheDir).apply { writeBytes(buffer.array()) }
        val anchor="task-mixed-silent"
        val initial=PlaybackOccurrenceInfo(anchor,"1","取票、提交待办",ReminderIntensity.GENTLE,30000,
            outputMode=ReminderOutputMode.VIBRATION,vibrationEventId="1",hasTaskCompletion=true)
        ReminderHaptics.testFactory={object:HapticOutput {
            override val capability="假马达，不驱动硬件"
            override fun start(pattern:RhythmPattern,settings:HapticSettings):String { started.countDown();return capability }
            override fun stop() { motorStops.incrementAndGet() }
        }}
        PlaybackDependencyRegistry.install(object:PlaybackServiceDependencies {
            override suspend fun loadOccurrence(occurrenceId:String)=initial
            override suspend fun preparePlayback(occurrenceId:String):PlaybackOccurrenceInfo { prepares.incrementAndGet();return initial }
            override suspend fun refreshPlayback(occurrenceId:String):PlaybackRefresh {
                assertEquals(anchor,occurrenceId)
                return PlaybackRefresh.Update(initial.copy(eventId="2",title="提交待办",outputMode=ReminderOutputMode.NOTIFICATION,
                    vibrationEventId=null,hasTaskCompletion=false,ringDurationMs=10000,continuation=true))
            }
            override suspend fun selectNextTrack(eventId:String):PlaybackTrackInfo { selections.incrementAndGet();return PlaybackTrackInfo("silent",android.net.Uri.fromFile(silent).toString(),"静音样本") }
            override suspend fun onPlaybackStarted(occurrenceId:String,trackId:String?)=true
            override suspend fun onTrackFailed(occurrenceId:String,trackId:String?,message:String)=Unit
            override suspend fun onPlaybackStopped(occurrenceId:String,reason:PlaybackStopReason) { stops.incrementAndGet();stopped.countDown() }
            override suspend fun createSnooze(occurrenceId:String,minutes:Int):AlarmRequest?=null
        })
        try {
            ContextCompat.startForegroundService(context,ReminderPlaybackService.startIntent(context,AlarmRequest(anchor,"1","同刻无声提醒",System.currentTimeMillis(),30000,ReminderIntensity.GENTLE,ReminderOutputMode.VIBRATION)))
            assertTrue(started.await(5,TimeUnit.SECONDS))
            TaskPlaybackChanges.publish()
            val remaining=withTimeout(5000) { PlaybackVisualState.current.first { it.occurrenceId==anchor && it.outputMode==ReminderOutputMode.NOTIFICATION } }
            assertFalse(remaining.hasTaskCompletion);assertEquals("提交待办",remaining.occurrence?.title)
            assertEquals(1,prepares.get());assertEquals(0,stops.get())
            assertEquals(0,selections.get());assertEquals(beforeAudio,ReminderPlaybackService.audioPlayerCreations)
            assertTrue(motorStops.get()>0)
            context.startService(ReminderPlaybackService.actionIntent(context,ReminderPlaybackService.ACTION_STOP,anchor))
            assertTrue(stopped.await(5,TimeUnit.SECONDS));assertEquals(1,stops.get())
        } finally {
            context.stopService(Intent(context,ReminderPlaybackService::class.java))
            ReminderHaptics.testFactory=previousMotor
            if(previous==null) PlaybackDependencyRegistry.clear() else PlaybackDependencyRegistry.install(previous)
            scenario.close()
        }
    }
}
