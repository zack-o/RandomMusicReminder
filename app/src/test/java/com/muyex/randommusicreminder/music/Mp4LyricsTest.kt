package com.muyex.randommusicreminder.music

import java.nio.ByteBuffer
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class Mp4LyricsTest {
    private fun box(type: String, body: ByteArray): ByteArray = ByteBuffer.allocate(8 + body.size).putInt(8 + body.size).put(type.toByteArray(Charsets.ISO_8859_1)).put(body).array()
    @Test fun readsNestedItunesLyricsWithoutReadingMediaPayload() {
        val text = "[00:01.50]本地歌词"
        val data = box("data", ByteBuffer.allocate(8).putInt(1).putInt(0).array() + text.toByteArray())
        val bytes = box("ftyp", "M4A ".toByteArray()) + box("mdat", ByteArray(100)) + box("moov", box("udta", box("meta", ByteArray(4) + box("ilst", box("©lyr", data)))))
        val file = Files.createTempFile("lexing-lyrics-", ".m4a").toFile()
        try { file.writeBytes(bytes); assertEquals(text, readMp4Lyrics(file)) } finally { file.delete() }
    }
    @Test fun malformedOrOversizedBoxStopsSafely() {
        val file = Files.createTempFile("lexing-bad-", ".m4a").toFile()
        try { file.writeBytes(ByteBuffer.allocate(8).putInt(Int.MAX_VALUE).put("moov".toByteArray()).array()); assertNull(readMp4Lyrics(file)) } finally { file.delete() }
    }
}
