package com.muyex.randommusicreminder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.muyex.randommusicreminder.data.local.entity.AiActionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiActionDao {
    @Insert suspend fun insert(value: AiActionEntity)
    @Query("SELECT * FROM ai_actions ORDER BY createdAtEpochMillis DESC LIMIT 50")
    fun observe(): Flow<List<AiActionEntity>>
    @Query("SELECT * FROM ai_actions WHERE id = :id") suspend fun get(id: String): AiActionEntity?
    @Query("UPDATE ai_actions SET state = 'UNDONE' WHERE id = :id AND state = 'APPLIED'") suspend fun markUndone(id: String): Int
}
