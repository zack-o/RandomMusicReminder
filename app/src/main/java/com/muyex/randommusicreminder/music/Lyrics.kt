package com.muyex.randommusicreminder.music

import java.nio.charset.Charset

data class LyricLine(val timeMillis: Long, val text: String)
data class LyricsDocument(val plainText: String = "", val lines: List<LyricLine> = emptyList()) {
    fun activeIndex(positionMillis: Long): Int {
        var low = 0; var high = lines.lastIndex; var found = -1
        while (low <= high) { val middle = (low + high) ushr 1; if (lines[middle].timeMillis <= positionMillis) { found = middle; low = middle + 1 } else high = middle - 1 }
        return found
    }
}

object Lyrics {
    private val timestamp = Regex("\\[(\\d{1,3}):([0-5]\\d)(?:[.:](\\d{1,3}))?]")
    private val offset = Regex("(?i)\\[offset:([+-]?\\d+)]")
    private val tag = Regex("(?i)\\[(?:ar|al|ti|by|re|ve|length|offset):[^]]*]")
    fun parse(input: String): LyricsDocument {
        val text = input.take(100_000).trim('\u0000', '\uFEFF', ' ', '\n', '\r')
        val shift = offset.find(text)?.groupValues?.get(1)?.toLongOrNull()?.coerceIn(-600_000, 600_000) ?: 0L
        val lines = mutableListOf<LyricLine>(); val plain = mutableListOf<String>()
        text.lineSequence().take(4000).forEach { row ->
            val words = tag.replace(timestamp.replace(row, ""), "").trim()
            if (words.isNotEmpty()) plain += words
            if (lines.size < 8000) timestamp.findAll(row).take(8).forEach { match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                lines += LyricLine((minutes * 60_000 + seconds * 1000 + fraction + shift).coerceAtLeast(0), words)
            }
        }
        return LyricsDocument(plain.joinToString("\n"), lines.sortedBy { it.timeMillis })
    }
}

/** ID3 USLT：编码字节、三字节语言、零结尾描述符、歌词；UTF16 描述符以两个零结束。 */
internal fun decodeUslt(data: ByteArray): String? {
    if (data.size !in 5..262_144) return null
    val encoding = data[0].toInt() and 255
    if (encoding !in 0..3) return null
    val wide = encoding in 1..2
    var index = 4
    while (index + (if (wide) 1 else 0) < data.size) {
        if (data[index] == 0.toByte() && (!wide || data[index + 1] == 0.toByte())) break
        index += if (wide) 2 else 1
    }
    val start = index + if (wide) 2 else 1
    if (start >= data.size) return null
    val charset = when (encoding) {
        0 -> Charsets.ISO_8859_1
        1 -> if (data.size > 5 && data[4] == 0xff.toByte() && data[5] == 0xfe.toByte()) Charset.forName("UTF-16LE") else Charset.forName("UTF-16BE")
        2 -> Charset.forName("UTF-16BE")
        else -> Charsets.UTF_8
    }
    return String(data, start, data.size - start, charset).trim('\u0000', '\uFEFF').takeIf { it.isNotBlank() }?.take(100_000)
}
