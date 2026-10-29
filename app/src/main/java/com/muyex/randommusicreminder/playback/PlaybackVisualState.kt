package com.muyex.randommusicreminder.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlayingVisual(val occurrenceId: String = "", val uri: String = "", val name: String = "", val positionMillis: Long = 0, val outputMode: com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode? = null, val outputSources: String? = null, val outputStatus: String? = null,val hasTaskCompletion:Boolean=false,val occurrence:PlaybackOccurrenceInfo?=null)

/** 与服务同进程的只读显示状态；只存定位信息，封面和歌词不进入 Intent 大字段。 */
object PlaybackVisualState {
    private val state = MutableStateFlow(NowPlayingVisual())
    val current = state.asStateFlow()
    internal fun publish(value: NowPlayingVisual) { state.value = value }
    internal fun clear() { state.value = NowPlayingVisual() }
}
