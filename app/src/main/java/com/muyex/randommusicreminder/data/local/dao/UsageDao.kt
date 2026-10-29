package com.muyex.randommusicreminder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.muyex.randommusicreminder.data.local.entity.UsageDailyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage_daily ORDER BY date DESC")
    fun observeAll(): Flow<List<UsageDailyEntity>>

    @Query("SELECT * FROM usage_daily WHERE date = :date")
    suspend fun getByDate(date: String): UsageDailyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: UsageDailyEntity)
}
