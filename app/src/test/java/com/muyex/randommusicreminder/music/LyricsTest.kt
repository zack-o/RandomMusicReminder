package com.muyex.randommusicreminder.music

import org.junit.Assert.*
import org.junit.Test

class LyricsTest {
    @Test fun fractionMultipleTagsOffsetAndActiveLine() {
        val lyrics = Lyrics.parse("[ti:测试]\n[offset:-500]\n[00:12.50][00:20.000]歌词\n[00:00]开始")
        assertEquals(listOf(0L, 12000L, 19500L), lyrics.lines.map { it.timeMillis })
        assertEquals(1, lyrics.activeIndex(12500)); assertEquals(-1, lyrics.activeIndex(-1))
        assertFalse(lyrics.plainText.contains("[ti:"))
    }
    @Test fun plainLyricsAndMalformedTagsDoNotInventTimestamps() {
        val lyrics = Lyrics.parse("第一行\n[00:99]错误时间\n第二行")
        assertTrue(lyrics.lines.isEmpty()); assertTrue(lyrics.plainText.contains("第一行"))
    }
    @Test fun parserHasTextAndLineBounds() {
        assertTrue(Lyrics.parse("词".repeat(120000)).plainText.length <= 100000)
        assertTrue(Lyrics.parse("[00:01]词\n".repeat(5000)).lines.size <= 4000)
    }
    @Test fun usltUtf8SkipsLanguageAndDescription() {
        val bytes = byteArrayOf(3) + "eng说明\u0000[00:12.50]歌词".toByteArray(Charsets.UTF_8)
        assertEquals("[00:12.50]歌词", decodeUslt(bytes))
        assertEquals(12500L, Lyrics.parse(checkNotNull(decodeUslt(bytes))).lines.single().timeMillis)
        assertNull(decodeUslt(byteArrayOf(3, 0)))
    }
    @Test fun utf16UsltPreservesChineseAfterWideTerminator() {
        val bytes = byteArrayOf(1) + "eng".toByteArray() + "描述".toByteArray(Charsets.UTF_16) + byteArrayOf(0, 0) + "歌词".toByteArray(Charsets.UTF_16)
        assertEquals("歌词", decodeUslt(bytes))
    }
}
