package com.muyex.randommusicreminder.data.repository

import androidx.room.withTransaction
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.local.entity.UsageDailyEntity
import com.muyex.randommusicreminder.domain.statistics.splitUsage
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.map

class UsageRepository(private val database: AppDatabase) {
    fun observeDaily() = database.usageDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun record(start: Instant, elapsedMillis: Long, launch: Boolean, zone: ZoneId) = database.withTransaction {
        val parts = splitUsage(start, elapsedMillis, zone).toMutableMap()
        val launchDate = start.atZone(zone).toLocalDate()
        if (launch) parts.putIfAbsent(launchDate, 0)
        parts.forEach { (date, millis) ->
            val previous = database.usageDao().getByDate(date.toString())
            database.usageDao().upsert(UsageDailyEntity(
                date = date.toString(),
                launches = (previous?.launches ?: 0) + if (launch && date == launchDate) 1 else 0,
                foregroundMillis = (previous?.foregroundMillis ?: 0) + millis,
            ))
        }
    }
}
