package com.muyex.randommusicreminder.music

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicImporterIntegrationTest {
    @Test
    fun importedAudioIsCopiedWithoutChangingSource() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "source-test.wav")
        writeSilentWav(source)
        val sourceLength = source.length()

        val importer = MusicImporter(context)
        val imported = importer.import(Uri.fromFile(source))
        val copied = requireNotNull(imported.uri.path).let(::File)

        assertTrue(source.exists())
        assertTrue(copied.exists())
        assertFalse(source.canonicalPath == copied.canonicalPath)
        assertTrue(sourceLength == copied.length())
        assertTrue(importer.remove(imported))
        assertTrue(source.exists())
    }

    private fun writeSilentWav(file: File) {
        val sampleRate = 8_000
        val dataSize = sampleRate / 10 * 2
        val header = ByteBuffer.allocate(44)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray())
            .putInt(36 + dataSize)
            .put("WAVEfmt ".toByteArray())
            .putInt(16)
            .putShort(1.toShort())
            .putShort(1.toShort())
            .putInt(sampleRate)
            .putInt(sampleRate * 2)
            .putShort(2.toShort())
            .putShort(16.toShort())
            .put("data".toByteArray())
            .putInt(dataSize)
            .array()
        FileOutputStream(file).use { output ->
            output.write(header)
            output.write(ByteArray(dataSize))
        }
    }
}
