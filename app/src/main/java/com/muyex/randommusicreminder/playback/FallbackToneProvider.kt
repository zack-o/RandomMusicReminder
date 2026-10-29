package com.muyex.randommusicreminder.playback

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** 生成随 APK 逻辑内置的短提示音，避免音乐池失效时完全依赖厂商系统铃声。 */
object FallbackToneProvider {
    @Synchronized
    fun uri(context: Context): Uri? = runCatching {
        val directory = File(context.filesDir, DIRECTORY_NAME)
        check(directory.exists() || directory.mkdirs()) { "无法创建兜底铃声目录" }
        val target = File(directory, FILE_NAME)
        if (!target.isFile || target.length() <= WAV_HEADER_SIZE) {
            writeTone(directory, target)
        }
        Uri.fromFile(target)
    }.getOrNull()

    private fun writeTone(directory: File, target: File) {
        val totalSamples = (SAMPLE_RATE * DURATION_SECONDS).toInt()
        val dataSize = totalSamples * BYTES_PER_SAMPLE
        val buffer = ByteBuffer.allocate(WAV_HEADER_SIZE + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(SAMPLE_RATE * BYTES_PER_SAMPLE)
        buffer.putShort(BYTES_PER_SAMPLE.toShort())
        buffer.putShort(BITS_PER_SAMPLE.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)

        repeat(totalSamples) { index ->
            val elapsed = index.toDouble() / SAMPLE_RATE
            val phaseInPulse = elapsed % PULSE_SECONDS
            val pulseEnvelope = if (phaseInPulse < AUDIBLE_SECONDS) {
                min(
                    1.0,
                    min(phaseInPulse / FADE_SECONDS, (AUDIBLE_SECONDS - phaseInPulse) / FADE_SECONDS),
                )
            } else {
                0.0
            }
            val edgeSamples = (SAMPLE_RATE * FADE_SECONDS).toInt()
            val fadeIn = min(1.0, index.toDouble() / edgeSamples)
            val fadeOut = min(1.0, (totalSamples - index).toDouble() / edgeSamples)
            val frequency = if ((elapsed / NOTE_SECONDS).toInt() % 2 == 0) 523.25 else 659.25
            val value = sin(2.0 * PI * frequency * elapsed) *
                pulseEnvelope * fadeIn * fadeOut * AMPLITUDE
            buffer.putShort((value * Short.MAX_VALUE).toInt().toShort())
        }

        buffer.flip()
        val temporary = File.createTempFile("fallback-", ".wav", directory)
        try {
            FileOutputStream(temporary).channel.use { channel ->
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                check(temporary.delete()) { "无法清理兜底铃声临时文件" }
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private const val DIRECTORY_NAME = "built_in_audio"
    private const val FILE_NAME = "lexing_fallback_v1.wav"
    private const val SAMPLE_RATE = 16_000
    private const val BITS_PER_SAMPLE = 16
    private const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
    private const val WAV_HEADER_SIZE = 44
    private const val DURATION_SECONDS = 2.4
    private const val PULSE_SECONDS = 0.8
    private const val AUDIBLE_SECONDS = 0.56
    private const val NOTE_SECONDS = 0.4
    private const val FADE_SECONDS = 0.04
    private const val AMPLITUDE = 0.2
}
