package com.muyex.randommusicreminder.data.transfer

import com.muyex.randommusicreminder.domain.model.ScheduleType
import com.muyex.randommusicreminder.domain.planner.*
import com.muyex.randommusicreminder.domain.time.ReminderClock
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

object DayOverrideCodec {
    fun encode(values:List<DayOverride>):String=JSONArray().apply { values.sortedBy { it.date }.forEach { value ->
        put(JSONObject().put("date",value.date.toString()).put("kind",value.kind.name).put("day",value.day?.let { day ->
            JSONObject().put("label",day.label).put("rest",day.rest).put("enabled",day.enabled).put("times",JSONArray().apply { day.times.forEach { slot ->
                put(JSONObject().put("time",ReminderClock.format(slot.time)).put("nextDay",slot.nextDay).put("key",slot.key))
            } }) } ?: JSONObject.NULL))
    } }.toString().also { require(it.length<=100_000) { "单日调整配置过长，请减少日期或时刻" } }
    fun decode(value:String):List<DayOverride> {
        require(value.length<=100_000) { "单日调整配置过长" }
        val rows=JSONArray(value);require(rows.length()<=DayOverrides.MAX_DATES)
        return (0 until rows.length()).map { index ->
            val row=rows.getJSONObject(index)
            val day=if(row.isNull("day")) null else row.getJSONObject("day").let { item ->
                val times=item.getJSONArray("times");require(times.length()<=8)
                ShiftDay(item.str("label"),item.bool("rest"),item.bool("enabled"),(0 until times.length()).map { i ->
                    val slot=times.getJSONObject(i)
                    ShiftTime(requireNotNull(ReminderClock.parse(slot.str("time"))),slot.bool("nextDay"),slot.str("key"))
                })
            }
            DayOverride(LocalDate.parse(row.str("date")),DayOverrideKind.valueOf(row.str("kind")),day)
        }.also { DayOverrides.validate(it,ScheduleType.SHIFT) }
    }
    fun encodeBarriers(values:Map<LocalDate,Long>):String=JSONObject().apply { values.toSortedMap().forEach { (date,time)->put(date.toString(),time) } }.toString()
    fun decodeBarriers(value:String):Map<LocalDate,Long> {
        require(value.length<=200_000)
        val root=JSONObject(value);require(root.length()<=5000)
        return root.keys().asSequence().associate { LocalDate.parse(it) to root.long(it).also { time->require(time>=0) } }
    }
}
