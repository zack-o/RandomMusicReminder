package com.muyex.randommusicreminder.music

import java.io.File
import java.io.RandomAccessFile

/** 有界读取 iTunes/M4A 的 ©lyr/data；跳过 mdat，不把整首音频读入内存。 */
internal fun readMp4Lyrics(file: File): String? = runCatching {
    RandomAccessFile(file, "r").use { input ->
        var boxes = 0
        fun scan(start: Long, end: Long, depth: Int, inLyrics: Boolean): String? {
            if (depth > 8) return null
            var position = start
            while (position <= end - 8 && boxes++ < 5000) {
                input.seek(position)
                var size = input.readInt().toLong() and 0xffffffffL
                val bytes = ByteArray(4); input.readFully(bytes)
                val type = String(bytes, Charsets.ISO_8859_1)
                var header = 8L
                if (size == 1L) { if (position > end - 16) return null; size = input.readLong(); header = 16 }
                if (size == 0L) size = end - position
                if (size < header || size > end - position) return null
                val content = position + header
                val limit = position + size
                if (inLyrics && type == "data" && size >= header + 8 && size - header - 8 <= 262_144) {
                    input.seek(content)
                    val encoding = input.readInt() and 0xffffff
                    input.readInt() // locale
                    if (encoding == 1 || encoding == 2) {
                        val text = ByteArray((size - header - 8).toInt()); input.readFully(text)
                        return String(text, if (encoding == 2) Charsets.UTF_16BE else Charsets.UTF_8).trim('\u0000', '\uFEFF').take(100_000).takeIf { it.isNotBlank() }
                    }
                }
                if (type in setOf("moov", "udta", "meta", "ilst", "©lyr")) {
                    val nestedStart = content + if (type == "meta") 4 else 0
                    if (nestedStart <= limit) scan(nestedStart, limit, depth + 1, inLyrics || type == "©lyr")?.let { return it }
                }
                position = limit
            }
            return null
        }
        scan(0, input.length(), 0, false)
    }
}.getOrNull()
