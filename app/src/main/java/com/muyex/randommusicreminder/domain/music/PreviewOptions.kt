package com.muyex.randommusicreminder.domain.music

data class PreviewOptions(
    val durationSeconds: Int = 5,
    val volumePercent: Int = 5,
    val startSeconds: Int = 0,
) {
    init {
        require(durationSeconds in 1..300) { "试听时长须为 1～300 秒" }
        require(volumePercent in 0..100) { "试听音量须为 0～100%" }
        require(startSeconds in 0..86_400) { "试听起点须为 0～86400 秒" }
    }

    fun endPositionMillis(trackDurationMillis: Long): Long {
        require(startSeconds * 1_000L < trackDurationMillis) { "试听起点已超出这首音乐的时长" }
        return minOf(trackDurationMillis, (startSeconds.toLong() + durationSeconds) * 1_000L)
    }
}
