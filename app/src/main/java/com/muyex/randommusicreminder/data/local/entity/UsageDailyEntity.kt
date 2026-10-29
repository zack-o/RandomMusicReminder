package com.muyex.randommusicreminder.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.muyex.randommusicreminder.domain.statistics.DailyUsage
import java.time.LocalDate

@Entity(tableName = "usage_daily")
data class UsageDailyEntity(
    @PrimaryKey val date: String,
    val launches: Int,
    val foregroundMillis: Long,
) {
    fun toDomain() = DailyUsage(LocalDate.parse(date), launches, foregroundMillis)
}
