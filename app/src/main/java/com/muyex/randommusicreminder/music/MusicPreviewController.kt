package com.muyex.randommusicreminder.music

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.muyex.randommusicreminder.domain.music.PreviewOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MusicPreviewState(
    val uri: Uri? = null,
    val isPlaying: Boolean = false,
    val errorMessage: String? = null,
)

class MusicPreviewController(context: Context) : AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var released = false
    private val autoStop = Runnable { stop() }
    private val mutableState = MutableStateFlow(MusicPreviewState())
    val state: StateFlow<MusicPreviewState> = mutableState.asStateFlow()

    private val playerHolder: Lazy<ExoPlayer> = lazy { ExoPlayer.Builder(context.applicationContext).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
        setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true,
        )
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                mutableState.value = mutableState.value.copy(isPlaying = isPlaying)
            }

            override fun onPlayerError(error: PlaybackException) {
                mainHandler.removeCallbacks(autoStop)
                player.stop()
                player.clearMediaItems()
                mutableState.value = MusicPreviewState(
                    errorMessage = "音乐预览失败：${error.errorCodeName}",
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    stop()
                }
            }
        })
    } }

    private val player: ExoPlayer get()=playerHolder.value

    fun play(uri: Uri, options: PreviewOptions = PreviewOptions(), trackDurationMillis: Long = Long.MAX_VALUE) = runOnMain {
        if (released) return@runOnMain
        val endMillis = options.endPositionMillis(trackDurationMillis)
        mainHandler.removeCallbacks(autoStop)
        mutableState.value = MusicPreviewState(uri = uri)
        player.volume = options.volumePercent / 100f
        player.setMediaItem(MediaItem.Builder().setUri(uri).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(options.startSeconds * 1_000L)
                .setEndPositionMs(endMillis).build(),
        ).build())
        player.prepare()
        player.play()
        // 片段自然结束之外再设墙钟上限，加载或音频焦点暂停也不会无限保留试听。
        mainHandler.postDelayed(autoStop, options.durationSeconds * 1_000L)
    }

    fun pause() = runOnMain {
        if (!released && playerHolder.isInitialized()) player.pause()
    }

    fun stop(): Unit = runOnMain {
        if (released) return@runOnMain
        mainHandler.removeCallbacks(autoStop)
        if(playerHolder.isInitialized()) { player.stop();player.clearMediaItems() }
        mutableState.value = MusicPreviewState()
    }

    override fun close() = release()

    fun release() = runOnMain {
        if (released) return@runOnMain
        mainHandler.removeCallbacks(autoStop)
        released = true
        if(playerHolder.isInitialized()) player.release()
        mutableState.value = MusicPreviewState()
    }

    private inline fun runOnMain(crossinline action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post { action() }
        }
    }
}
