package com.muyex.randommusicreminder.music

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class EmbeddedMetadataDeviceTest {
    @Test fun selectedLocalSampleHasCoverAndEmbeddedLyricsWithoutPlayback() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("lexingMetadataSample") == "1")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "lexing-metadata-sample.flac")
        require(source.isFile) { "未放入明确选择的本地测试音频" }
        val directory = File(context.filesDir, "imported_music").apply { mkdirs() }
        val copy = File(directory, "metadata-test-${UUID.randomUUID()}.flac")
        try {
            source.copyTo(copy)
            val metadata = MusicMetadataReader.read(context, android.net.Uri.fromFile(copy).toString())
            assertNotNull("未读取到内嵌封面", metadata.artwork)
            assertTrue("未读取到内嵌歌词", metadata.lyrics.plainText.isNotBlank())
        } finally { copy.delete() }
    }
}
