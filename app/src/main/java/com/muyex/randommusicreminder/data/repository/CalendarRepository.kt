package com.muyex.randommusicreminder.data.repository

import androidx.room.withTransaction
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.local.entity.toEntity
import com.muyex.randommusicreminder.domain.calendar.CalendarOverride
import com.muyex.randommusicreminder.domain.calendar.CalendarDayType
import java.time.LocalDate
import kotlinx.coroutines.flow.map

class CalendarRepository(private val database: AppDatabase) {
    fun observe() = database.calendarDao().observe().map { rows -> rows.map { it.toDomain() } }
    suspend fun getAll() = database.calendarDao().getAll().map { it.toDomain() }

    suspend fun setDays(dates: List<LocalDate>, type: CalendarDayType?, note: String) = database.withTransaction {
        require(dates.isNotEmpty() && dates.size <= 366) { "每次选择 1～366 天" }
        require(dates.all { it.year in 1900..2200 } && note.length <= 500) { "日期或备注无效" }
        if (type == null) database.calendarDao().reset(dates.distinct().map(LocalDate::toString))
        else database.calendarDao().upsert(dates.distinct().map { CalendarOverride(it, type, note, "手动设置", System.currentTimeMillis()).toEntity() })
    }
}
