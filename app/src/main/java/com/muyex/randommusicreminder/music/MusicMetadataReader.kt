package com.muyex.randommusicreminder.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class EmbeddedMusicMetadata(val title: String = "", val artist: String = "", val album: String = "", val artwork: Bitmap? = null,
    val lyrics: LyricsDocument = LyricsDocument(), val issue: String? = null)

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
object MusicMetadataReader {
    private val cache = LruCache<String, EmbeddedMusicMetadata>(6)
    private val parallelReads = Semaphore(2)
    suspend fun read(context: Context, uriText: String): EmbeddedMusicMetadata = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriText)
        val root = File(context.filesDir, "imported_music").canonicalFile
        val file = if (uri.scheme == "file") uri.path?.let { File(it).canonicalFile } else null
        if (file == null || file.parentFile != root || !file.isFile) return@withContext EmbeddedMusicMetadata(issue = "没有可读取的本地音乐副本")
        val key = "$uriText:${file.length()}:${file.lastModified()}"
        cache.get(key)?.let { return@withContext it }
        val builder = MediaMetadata.Builder()
        var lyrics = ""
        var issue: String? = null
        try {
            parallelReads.withPermit { withTimeout(8000) {
                MetadataRetriever.Builder(context.applicationContext, MediaItem.fromUri(uri)).build().use { retriever ->
                    val groups = suspendCancellableCoroutine<TrackGroupArray> { continuation ->
                        val future = retriever.retrieveTrackGroups()
                        future.addListener({
                            try { val result = future.get(); if (continuation.isActive) continuation.resume(result) }
                            catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                        }, Executor { it.run() })
                        continuation.invokeOnCancellation { future.cancel(true) }
                    }
                    for (group in 0 until minOf(groups.length, 16)) {
                        val metadata = groups[group].getFormat(0).metadata ?: continue
                        for (index in 0 until minOf(metadata.length(), 300)) {
                            val entry = metadata[index]
                            entry.populateMediaMetadata(builder)
                            val candidate = when (entry) {
                                is BinaryFrame -> if (entry.id in setOf("USLT", "ULT")) decodeUslt(entry.data) else null
                                is TextInformationFrame -> if (entry.id in setOf("USLT", "ULT") || entry.description?.uppercase() in setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS")) entry.values.joinToString("\n") else null
                                is VorbisComment -> if (entry.key.uppercase() in setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS")) entry.value else null
                                is InternalFrame -> if (entry.description.uppercase().contains("LYRIC")) entry.text else null
                                else -> null
                            }
                            if (!candidate.isNullOrBlank() && lyrics.isBlank()) lyrics = candidate.take(100_000)
                        }
                    }
                }
            } }
        } catch (_: TimeoutCancellationException) { issue = "标签读取超时，已降级显示" }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { issue = "部分内嵌标签无法解析" }
        var metadata = builder.build()
        if (lyrics.isBlank() && file.extension.lowercase() in setOf("m4a", "mp4")) lyrics = readMp4Lyrics(file).orEmpty()
        var picture = metadata.artworkData
        if (issue == null && (picture == null || metadata.title == null)) {
            val platform = MediaMetadataRetriever()
            try {
                platform.setDataSource(context, uri)
                if (metadata.title == null) builder.setTitle(platform.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
                if (metadata.artist == null) builder.setArtist(platform.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
                if (metadata.albumTitle == null) builder.setAlbumTitle(platform.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM))
                if (picture == null) picture = platform.embeddedPicture
                metadata = builder.build()
            } catch (_: Exception) { /* 封面失败不得影响选曲和播放。 */ }
            finally { platform.release() }
        }
        EmbeddedMusicMetadata(metadata.title?.toString().orEmpty().take(500), metadata.artist?.toString().orEmpty().take(500),
            metadata.albumTitle?.toString().orEmpty().take(500), picture?.let(::decodeArtwork), Lyrics.parse(lyrics), issue).also { cache.put(key, it) }
    }

    private fun decodeArtwork(bytes: ByteArray): Bitmap? {
        if (bytes.size > 4 * 1024 * 1024) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..16_384 || bounds.outHeight !in 1..16_384) return null
        var sample = 1
        while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
        return try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) } catch (_: OutOfMemoryError) { null }
    }
}
