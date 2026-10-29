package com.muyex.randommusicreminder.data.transfer

import com.muyex.randommusicreminder.domain.planner.*
import com.muyex.randommusicreminder.domain.time.ReminderClock
import org.json.JSONArray
import org.json.JSONObject

object ShiftCycleCodec {
    fun encode(cycle: ShiftCycle?): String {
        if (cycle == null) return ""
        cycle.validate()
        return JSONArray().apply { cycle.days.forEach { day -> put(JSONObject().put("label",day.label).put("rest",day.rest)
            .put("enabled",day.enabled).put("times",JSONArray().apply { day.times.forEach { slot ->
                put(JSONObject().put("time",ReminderClock.format(slot.time)).put("nextDay",slot.nextDay).put("key",slot.key))
            } })) } }.toString()
    }
    fun decode(value: String): ShiftCycle? {
        if (value.isEmpty()) return null
        require(value.length <= 100_000) { "轮班配置过长" }
        val days=JSONArray(value)
        require(days.length() in 1..60) { "轮班周期须为1～60天" }
        return ShiftCycle((0 until days.length()).map { index ->
            val day=days.getJSONObject(index);val times=day.getJSONArray("times")
            require(times.length()<=8) { "每天最多8个轮班时刻" }
            ShiftDay(day.str("label"),day.bool("rest"),day.bool("enabled"),(0 until times.length()).map { i ->
                val slot=times.getJSONObject(i)
                ShiftTime(requireNotNull(ReminderClock.parse(slot.str("time"))) { "轮班时刻无效" },slot.bool("nextDay"),slot.str("key"))
            })
        }).also { it.validate() }
    }
}
