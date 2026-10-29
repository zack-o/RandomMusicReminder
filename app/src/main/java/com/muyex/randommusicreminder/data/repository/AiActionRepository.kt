package com.muyex.randommusicreminder.data.repository

import androidx.room.withTransaction
import com.muyex.randommusicreminder.ai.AiEditProposal
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.local.entity.AiActionEntity
import com.muyex.randommusicreminder.data.transfer.*
import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.domain.transfer.TransferRules
import org.json.JSONArray
import java.util.UUID

class AiActionRepository(private val database: AppDatabase, private val events: EventRepository) {
    fun observe() = database.aiActionDao().observe()
    suspend fun apply(proposal: AiEditProposal): Int = database.withTransaction {
        require(proposal.after.isNotEmpty() && proposal.after.size == proposal.before.size)
        proposal.before.forEach { verify(it) }
        proposal.after.forEach { events.saveEvent(it) }
        val applied = proposal.after.map { requireNotNull(events.getEvent(it.event.id)) }
        database.aiActionDao().insert(AiActionEntity(UUID.randomUUID().toString(), "修改 ${applied.size} 项提醒", System.currentTimeMillis(),
            beforeJson = encode(proposal.before), afterJson = encode(applied)))
        applied.size
    }

    suspend fun undo(id: String): Int = database.withTransaction {
        val record = database.aiActionDao().get(id) ?: error("操作记录不存在")
        require(record.state == "APPLIED") { "该操作已撤销" }
        val after = decode(record.afterJson)
        after.forEach { verify(it) }
        val before = decode(record.beforeJson)
        before.forEach { events.saveEvent(it) }
        check(database.aiActionDao().markUndone(id) == 1)
        before.size
    }

    private suspend fun verify(expected: EventDetails) {
        val current = events.getEvent(expected.event.id) ?: error("所选事件已删除，请重新预览")
        require(current.event.updatedAtEpochMillis == expected.event.updatedAtEpochMillis &&
            current.event.enabled == expected.event.enabled && current.event.timingRevision == expected.event.timingRevision && current.event.tags == expected.event.tags && current.event.trackCompletion == expected.event.trackCompletion && current.event.dayOverrides==expected.event.dayOverrides &&
            TransferRules.fingerprint(current) == TransferRules.fingerprint(expected)) { "提醒已被修改，本次操作不会覆盖新内容，请重新预览" }
    }

    private fun encode(items: List<EventDetails>) = JSONArray().apply { items.forEach { item ->
        put(BackupCodec.eventToJson(BackupEvent(item)).put("localId", item.event.id).put("localUpdated", item.event.updatedAtEpochMillis)
            .put("localRevision", item.event.timingRevision).put("notBefore", item.event.windowFirstNotBeforeEpochMillis ?: org.json.JSONObject.NULL)
            .put("trackIds", JSONArray(item.customTrackIds.toList())))
    } }.toString()

    private fun decode(text: String): List<EventDetails> {
        val array = JSONArray(text)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index); val details = BackupCodec.eventFromJson(item).details
            val tracks = item.getJSONArray("trackIds")
            details.copy(event = details.event.copy(id = item.getLong("localId"), updatedAtEpochMillis = item.getLong("localUpdated"),
                timingRevision = item.getLong("localRevision"), windowFirstNotBeforeEpochMillis = if (item.isNull("notBefore")) null else item.getLong("notBefore")),
                customTrackIds = (0 until tracks.length()).map { tracks.getLong(it) }.toSet())
        }
    }
}
