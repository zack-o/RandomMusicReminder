package com.muyex.randommusicreminder.music

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import com.muyex.randommusicreminder.domain.haptics.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Decodes into memory only. It has no AudioTrack, player, audio focus or network output. */
class PcmRhythmAnalyzer {
    suspend fun analyze(file: File, startSeconds: Int, lengthSeconds: Int): RhythmPattern {
        require(startSeconds in 0..86400 && lengthSeconds in 10..30) { "请选择0～86400秒起点、10～30秒片段" }
        val extractor=MediaExtractor()
        var codec: MediaCodec?=null
        val startUs=startSeconds*1_000_000L;val endUs=startUs+lengthSeconds*1_000_000L
        val power=DoubleArray(lengthSeconds*50);val counts=IntArray(power.size);var lastFrame=-1
        val deadline=SystemClock.elapsedRealtime()+20_000
        try {
            extractor.setDataSource(file.absolutePath)
            val track=(0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true }
                ?: error("文件中没有可解码音轨")
            val format=extractor.getTrackFormat(track);extractor.selectTrack(track)
            extractor.seekTo(startUs,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);var channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding=if(format.containsKey(MediaFormat.KEY_PCM_ENCODING)) format.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
            fun validateFormat() { require(rate in 8000..192000 && channels in 1..8 && encoding in setOf(AudioFormat.ENCODING_PCM_16BIT,AudioFormat.ENCODING_PCM_FLOAT)) { "暂不支持此PCM采样或声道格式" } }
            fun accumulate(buffer: ByteBuffer, timeUs: Long) {
                validateFormat();buffer.order(ByteOrder.nativeOrder())
                val bytes=if(encoding==AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                var sample=0L
                while(buffer.remaining()>=bytes) {
                    val value=if(bytes==4) buffer.float.toDouble().coerceIn(-1.0,1.0) else buffer.short.toDouble()/32768.0
                    val relative=timeUs+sample/channels*1_000_000L/rate-startUs
                    if(relative>=0) {
                        val index=(relative/20_000).toInt()
                        if(index in power.indices && value.isFinite()) { power[index]+=value*value;counts[index]++;lastFrame=maxOf(lastFrame,index) }
                    }
                    sample++
                }
            }
            val mime=requireNotNull(format.getString(MediaFormat.KEY_MIME))
            if(mime=="audio/raw") {
                validateFormat();val buffer=ByteBuffer.allocateDirect(1024*1024)
                while(extractor.sampleTime>=0 && extractor.sampleTime<endUs) {
                    currentCoroutineContext().ensureActive();check(SystemClock.elapsedRealtime()<deadline) { "分析超时，请选择较短片段" }
                    buffer.clear();val size=extractor.readSampleData(buffer,0);if(size<0) break
                    require(size<=buffer.capacity()) { "音频帧过大" };buffer.position(0);buffer.limit(size)
                    accumulate(buffer,extractor.sampleTime);extractor.advance()
                }
            } else {
                format.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT)
                val decoder=MediaCodec.createDecoderByType(mime);codec=decoder
                decoder.configure(format,null,null,0);decoder.start()
                var inputEnded=false;var outputEnded=false;val info=MediaCodec.BufferInfo()
                while(!outputEnded) {
                    currentCoroutineContext().ensureActive();check(SystemClock.elapsedRealtime()<deadline) { "分析超时，请换片段或预设" }
                    if(!inputEnded) {
                        val index=decoder.dequeueInputBuffer(10_000)
                        if(index>=0) {
                            val buffer=checkNotNull(decoder.getInputBuffer(index));buffer.clear()
                            val timestamp=extractor.sampleTime
                            val size=if(timestamp<0 || timestamp>=endUs+1_000_000) -1 else extractor.readSampleData(buffer,0)
                            if(size<0) { decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnded=true }
                            else { decoder.queueInputBuffer(index,0,size,timestamp,0);extractor.advance() }
                        }
                    }
                    val index=decoder.dequeueOutputBuffer(info,10_000)
                    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val output=decoder.outputFormat;rate=output.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        encoding=if(output.containsKey(MediaFormat.KEY_PCM_ENCODING)) output.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        validateFormat()
                    } else if(index>=0) {
                        try {
                            decoder.getOutputBuffer(index)?.let { buffer ->
                                buffer.position(info.offset);buffer.limit(info.offset+info.size)
                                if(info.size>0 && info.presentationTimeUs<endUs) accumulate(buffer,info.presentationTimeUs)
                            }
                            outputEnded=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || info.presentationTimeUs>=endUs
                        } finally { decoder.releaseOutputBuffer(index,false) }
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            val rms=(0..lastFrame).map { if(counts[it]==0) 0.0 else sqrt(power[it]/counts[it]).coerceIn(0.0,1.0) }
            return EnergyRhythm.fromRms(rms)
        } finally {
            codec?.let { runCatching { it.stop() };it.release() }
            extractor.release()
        }
    }
}
