package com.muyex.randommusicreminder.playback

import android.content.Context
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
import org.junit.Assert.*
import org.junit.Test

class SilentOutputIntegrationTest {
    @Test fun vibrationUsesFakeMotorAndNeverCreatesAudioOrSelectsMusic() = checkMode(ReminderOutputMode.VIBRATION,true)
    @Test fun notificationOnlyNeverCreatesAudioOrStartsMotor() = checkMode(ReminderOutputMode.NOTIFICATION,false)
    @Test fun missingMusicRhythmUsesPresetWithoutCreatingAudio() = checkMode(ReminderOutputMode.VIBRATION,true,true)

    private fun checkMode(mode:ReminderOutputMode,shouldVibrate:Boolean,musicRhythm:Boolean=false) {
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val context=ApplicationProvider.getApplicationContext<Context>()
        val original=PlaybackDependencyRegistry.current()
        val originalFactory=ReminderHaptics.testFactory
        val started=CountDownLatch(1);val stopped=CountDownLatch(1);val motorStarted=CountDownLatch(1)
        val selectCount=AtomicInteger();val motorCount=AtomicInteger();val cancelCount=AtomicInteger()
        val beforeAudio=ReminderPlaybackService.audioPlayerCreations
        // Even a broken audio branch receives silence, never the application's audible fallback.
        val buffer=ByteBuffer.allocate(8044).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(8036).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(8000).putInt(16000).putShort(2).putShort(16).put("data".toByteArray()).putInt(8000)
        val silent=File.createTempFile("haptic-silence-",".wav",context.cacheDir).apply { writeBytes(buffer.array()) }
        val id="silent-output-${mode.name}"
        ReminderHaptics.testFactory={object: HapticOutput {
            override val capability="假马达，不驱动硬件"
            override fun start(pattern:RhythmPattern,settings:HapticSettings):String { pattern.validate();motorCount.incrementAndGet();motorStarted.countDown();return capability }
            override fun stop() { cancelCount.incrementAndGet() }
        }}
        PlaybackDependencyRegistry.install(object: PlaybackServiceDependencies {
            override suspend fun loadOccurrence(occurrenceId:String)=PlaybackOccurrenceInfo(id,"1","无声通道测试",ReminderIntensity.GENTLE,500,isTest=true,outputMode=mode,haptics=HapticSettings(preset=HapticPreset.URGENT,useMusicRhythm=musicRhythm))
            override suspend fun selectNextTrack(eventId:String):PlaybackTrackInfo { selectCount.incrementAndGet();return PlaybackTrackInfo("silent",android.net.Uri.fromFile(silent).toString(),"静音样本") }
            override suspend fun onPlaybackStarted(occurrenceId:String,trackId:String?):Boolean { started.countDown();return true }
            override suspend fun onTrackFailed(occurrenceId:String,trackId:String?,message:String)=Unit
            override suspend fun onPlaybackStopped(occurrenceId:String,reason:PlaybackStopReason) { stopped.countDown() }
            override suspend fun createSnooze(occurrenceId:String,minutes:Int):AlarmRequest?=null
        })
        try {
            ContextCompat.startForegroundService(context,ReminderPlaybackService.startIntent(context,AlarmRequest(id,"1","无声测试",System.currentTimeMillis(),500,ReminderIntensity.GENTLE,mode)))
            assertTrue(started.await(5,TimeUnit.SECONDS))
            if(shouldVibrate) {
                assertTrue(motorStarted.await(5,TimeUnit.SECONDS))
                context.startService(ReminderPlaybackService.actionIntent(context,ReminderPlaybackService.ACTION_STOP,id))
            }
            assertTrue(stopped.await(5,TimeUnit.SECONDS))
            assertEquals(beforeAudio,ReminderPlaybackService.audioPlayerCreations)
            assertEquals(if(musicRhythm) 1 else 0,selectCount.get());assertEquals(if(shouldVibrate) 1 else 0,motorCount.get())
            if(shouldVibrate) assertTrue(cancelCount.get()>=1)
        } finally {
            context.stopService(android.content.Intent(context,ReminderPlaybackService::class.java))
            ReminderHaptics.testFactory=originalFactory
            if(original==null) PlaybackDependencyRegistry.clear() else PlaybackDependencyRegistry.install(original)
            scenario.close()
        }
    }
}
