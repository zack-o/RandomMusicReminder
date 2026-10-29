package com.muyex.randommusicreminder.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName="task_instances",primaryKeys=["eventId","taskKey"],foreignKeys=[ForeignKey(entity=EventEntity::class,parentColumns=["id"],childColumns=["eventId"],onDelete=ForeignKey.CASCADE)],indices=[Index("eventId")])
data class TaskInstanceEntity(val eventId:Long,val taskKey:String,val baseDate:LocalDate,val label:String,val taskAtEpochMillis:Long,val createdAtEpochMillis:Long)

@Entity(tableName="task_actions",indices=[Index(value=["eventId","taskKey","id"])])
data class TaskActionEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val eventId:Long,val taskKey:String,val baseDate:LocalDate,
    val occurrenceLabel:String,val titleSnapshot:String,val action:String,val happenedAtEpochMillis:Long)
