package com.muyex.randommusicreminder.data.local.dao

import androidx.room.*
import com.muyex.randommusicreminder.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Upsert suspend fun upsertInstances(values:List<TaskInstanceEntity>)
    @Query("SELECT * FROM task_instances WHERE eventId=:eventId AND taskKey=:key")
    suspend fun instance(eventId:Long,key:String):TaskInstanceEntity?
    @Insert suspend fun insertAction(value:TaskActionEntity):Long
    @Query("SELECT * FROM task_actions WHERE eventId=:eventId AND taskKey=:key ORDER BY id DESC LIMIT 1")
    suspend fun latest(eventId:Long,key:String):TaskActionEntity?
    @Query("SELECT * FROM task_actions WHERE id=:id")
    suspend fun action(id:Long):TaskActionEntity?
    @Query("SELECT a.* FROM task_actions a WHERE a.id=(SELECT MAX(b.id) FROM task_actions b WHERE b.eventId=a.eventId AND b.taskKey=a.taskKey)")
    suspend fun states():List<TaskActionEntity>
    @Query("SELECT * FROM task_actions ORDER BY id DESC LIMIT 1000")
    fun observeRecords():Flow<List<TaskActionEntity>>
    @Query("SELECT * FROM task_actions ORDER BY id DESC LIMIT 10000")
    suspend fun exportRecords():List<TaskActionEntity>
    @Query("""SELECT i.* FROM task_instances i
        WHERE i.eventId=:eventId AND ((:past=1 AND i.taskAtEpochMillis<:dayStart) OR (:past=0 AND i.taskAtEpochMillis>=:dayStart))
        AND COALESCE((SELECT a.action FROM task_actions a WHERE a.eventId=i.eventId AND a.taskKey=i.taskKey ORDER BY a.id DESC LIMIT 1),'REOPEN')!='DONE'
        ORDER BY CASE WHEN :descending=1 THEN -i.taskAtEpochMillis ELSE i.taskAtEpochMillis END LIMIT 60"""
    )
    suspend fun choices(eventId:Long,dayStart:Long,past:Boolean,descending:Boolean):List<TaskInstanceEntity>
}
