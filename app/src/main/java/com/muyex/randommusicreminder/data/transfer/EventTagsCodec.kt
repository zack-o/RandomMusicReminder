package com.muyex.randommusicreminder.data.transfer

import com.muyex.randommusicreminder.domain.organizer.EventTags
import org.json.JSONArray

object EventTagsCodec {
    fun encode(values: List<String>): String = JSONArray(EventTags.normalize(values)).toString()
    fun decode(value: String): List<String> {
        require(value.length <= 4096) { "标签数据过长" }
        val array = JSONArray(value)
        require(array.length() <= 8) { "每个事项最多8个标签" }
        return EventTags.normalize((0 until array.length()).map { index ->
            array.get(index).let { require(it is String) { "标签须为文本" }; it }
        })
    }
}
