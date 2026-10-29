package com.muyex.randommusicreminder.domain.organizer

import java.util.Locale

object EventTags {
    private fun clean(value: String) = value.trim().replace(Regex("\\s+"), " ")
    fun key(value: String): String = clean(value).lowercase(Locale.ROOT)

    fun normalize(values: List<String>): List<String> {
        val result = values.map(::clean).onEach {
            require(it.isNotBlank() && it.codePointCount(0, it.length) in 1..20 && it.none(Char::isISOControl)) { "每个标签须为1～20字的可见文本" }
        }.distinctBy(::key)
        require(result.size <= 8) { "每个事项最多8个标签" }
        return result
    }

    fun matches(title: String, note: String, tags: List<String>, query: String, selectedTags: Set<String>): Boolean {
        val keys = tags.mapTo(hashSetOf(), ::key)
        if (!selectedTags.all { key(it) in keys }) return false
        val searchable = "$title\n$note\n${tags.joinToString(" ")}"
        return query.trim().split(Regex("\\s+")).filter(String::isNotBlank).all { searchable.contains(it, ignoreCase = true) }
    }
}
