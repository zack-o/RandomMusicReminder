package com.muyex.randommusicreminder.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.muyex.randommusicreminder.data.local.entity.CalendarOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendar_overrides ORDER BY date")
    fun observe(): Flow<List<CalendarOverrideEntity>>

    @Query("SELECT * FROM calendar_overrides ORDER BY date")
    suspend fun getAll(): List<CalendarOverrideEntity>

    @Upsert
    suspend fun upsert(rows: List<CalendarOverrideEntity>)

    @Query("DELETE FROM calendar_overrides WHERE date IN (:dates)")
    suspend fun reset(dates: List<String>)
}
