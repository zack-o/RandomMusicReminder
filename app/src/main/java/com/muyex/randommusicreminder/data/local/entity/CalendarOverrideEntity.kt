package com.muyex.randommusicreminder.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.muyex.randommusicreminder.domain.calendar.CalendarOverride
import com.muyex.randommusicreminder.domain.calendar.CalendarDayType
import java.time.LocalDate

@Entity(tableName = "calendar_overrides")
data class CalendarOverrideEntity(
    @PrimaryKey val date: String,
    val dayType: String,
    val note: String,
    val source: String,
    val updatedAt: Long,
) {
    fun toDomain() = CalendarOverride(LocalDate.parse(date), CalendarDayType.valueOf(dayType), note, source, updatedAt)
}

fun CalendarOverride.toEntity() = CalendarOverrideEntity(date.toString(), type.name, note, source, updatedAt)
