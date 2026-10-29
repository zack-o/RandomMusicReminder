package com.muyex.randommusicreminder.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_actions")
data class AiActionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAtEpochMillis: Long,
    val state: String = "APPLIED",
    val beforeJson: String,
    val afterJson: String,
)
