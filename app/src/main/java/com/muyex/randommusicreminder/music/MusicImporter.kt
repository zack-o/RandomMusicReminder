package com.muyex.randommusicreminder.music

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID

data class ImportedMusic(
    val id: String,
    val displayName: String,
    val uri: Uri,
    val mimeType: String?,
    val durationMs: Long?,
    val fileSizeBytes: Long,
    val importedAtEpochMs: Long,
)

class MusicImportException(message: String, cause: Throwable? = null) : IOException(message, cause)

class MusicImporter(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val musicDirectory = File(appContext.filesDir, DIRECTORY_NAME)

    suspend fun displayName(uri: Uri): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        readOpenableMetadata(uri).displayName
    }

    suspend fun import(uri: Uri): ImportedMusic = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val metadata = readOpenableMetadata(uri)
        val mimeType = resolver.getType(uri)
        if (mimeType != null &&
            !mimeType.startsWith("audio/") &&
            mimeType != "application/octet-stream"
        ) {
            throw MusicImportException("所选文件不是可识别的音频文件")
        }
        if (!musicDirectory.exists() && !musicDirectory.mkdirs()) {
            throw MusicImportException("无法创建音乐存储目录")
        }

        val id = UUID.randomUUID().toString()
        val extension = resolveExtension(metadata.displayName, mimeType)
        val destination = File(musicDirectory, id + extension)
        val temporary = File.createTempFile("import-", ".tmp", musicDirectory)

        try {
            val bytesCopied = resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE).also { output.fd.sync() }
                }
            } ?: throw MusicImportException("无法读取所选音乐")

            if (bytesCopied <= 0L) throw MusicImportException("所选音乐为空")
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = false)
                if (!temporary.delete()) temporary.deleteOnExit()
            }

            ImportedMusic(
                id = id,
                displayName = metadata.displayName,
                uri = Uri.fromFile(destination),
                mimeType = mimeType,
                durationMs = readDuration(uri),
                fileSizeBytes = destination.length(),
                importedAtEpochMs = System.currentTimeMillis(),
            )
        } catch (error: Throwable) {
            temporary.delete()
            destination.delete()
            if (error is MusicImportException) throw error
            throw MusicImportException("导入音乐失败", error)
        }
    }

    suspend fun remove(importedMusic: ImportedMusic): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val path = importedMusic.uri.path
        if (path == null) {
            false
        } else {
            val file = File(path).canonicalFile
            val root = musicDirectory.canonicalFile
            val isOwnedFile = file.parentFile == root && file.name.startsWith(importedMusic.id)
            isOwnedFile && (!file.exists() || file.delete())
        }
    }

    private fun readOpenableMetadata(uri: Uri): OpenableMetadata {
        var displayName: String? = null
        var size: Long? = null
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) displayName = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        val safeName = sanitizeName(displayName ?: uri.lastPathSegment ?: "本地音乐")
        return OpenableMetadata(safeName, size)
    }

    private fun readDuration(uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun resolveExtension(displayName: String, mimeType: String?): String {
        val fromMime = mimeType?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType)
        val fromName = displayName.substringAfterLast('.', missingDelimiterValue = "")
        val candidate = (fromMime ?: fromName)
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() }
            .take(8)
        return if (candidate.isBlank()) ".audio" else ".$candidate"
    }

    private fun sanitizeName(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        .trim()
        .take(120)
        .ifBlank { "本地音乐" }

    private data class OpenableMetadata(val displayName: String, val declaredSize: Long?)

    internal companion object {
        const val DIRECTORY_NAME = "imported_music"
    }
}
