package com.muyex.randommusicreminder.data.repository

import androidx.room.withTransaction
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.local.entity.MusicTrackEntity
import com.muyex.randommusicreminder.domain.model.MusicPoolMode
import com.muyex.randommusicreminder.domain.model.MusicTrack
import com.muyex.randommusicreminder.domain.model.ShuffleState
import com.muyex.randommusicreminder.domain.selection.TrackSelector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MusicRepository(
    private val database: AppDatabase,
    private val trackSelector: TrackSelector = TrackSelector(),
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val musicDao = database.musicDao()
    private val eventDao = database.eventDao()

    fun observeTracks(): Flow<List<MusicTrack>> {
        return musicDao.observeAll().map { rows -> rows.map { it.toDomain() } }
    }

    suspend fun getTracks(): List<MusicTrack> = musicDao.getAll().map { it.toDomain() }

    suspend fun getTrack(trackId: Long): MusicTrack? = musicDao.getById(trackId)?.toDomain()

    suspend fun saveTrack(track: MusicTrack): Long = database.withTransaction {
        require(track.displayName.isNotBlank()) { "Track name must not be blank" }
        require(track.uri.isNotBlank()) { "Track URI must not be blank" }
        require(track.durationMillis > 0) { "Track duration must be positive" }

        val byUri = musicDao.getByUri(track.uri)
        val targetId = when {
            track.id > 0 -> track.id
            byUri != null -> byUri.id
            else -> 0
        }
        val entity = MusicTrackEntity(
            id = targetId,
            displayName = track.displayName.trim(),
            uri = track.uri,
            mimeType = track.mimeType,
            durationMillis = track.durationMillis,
            fileSizeBytes = track.fileSizeBytes,
            enabled = track.enabled,
            importedAtEpochMillis = byUri?.importedAtEpochMillis
                ?: track.importedAtEpochMillis.takeIf { it > 0 }
                ?: nowEpochMillis(),
            invalidReason = track.invalidReason,
        )
        if (targetId == 0L) {
            musicDao.insert(entity)
        } else {
            require(musicDao.getById(targetId) != null) { "Track $targetId does not exist" }
            musicDao.update(entity)
            targetId
        }
    }

    suspend fun setEnabled(trackId: Long, enabled: Boolean): Boolean {
        return musicDao.setEnabled(trackId, enabled) > 0
    }

    suspend fun setEnabled(trackIds: List<Long>, enabled: Boolean): Int = database.withTransaction {
        // 分批避免超出旧版 SQLite 的绑定参数上限；事务保证整组修改一致。
        trackIds.distinct().filter { it > 0 }.chunked(500).sumOf { musicDao.setEnabledBulk(it, enabled) }
    }

    suspend fun markInvalid(trackId: Long, reason: String?): Boolean {
        return musicDao.setInvalidReason(trackId, reason) > 0
    }

    suspend fun deleteTrack(trackId: Long): Boolean = musicDao.delete(trackId) > 0

    suspend fun getEnabledTracksForEvent(eventId: Long): List<MusicTrack> {
        val event = eventDao.getWithRelations(eventId)?.event ?: return emptyList()
        val rows = if (event.musicPoolMode == MusicPoolMode.CUSTOM) {
            musicDao.getEnabledForEvent(eventId)
        } else {
            musicDao.getEnabledGlobal()
        }
        return rows.map { it.toDomain() }
    }

    suspend fun selectNextTrack(eventId: Long): MusicTrack? = database.withTransaction {
        val event = eventDao.getWithRelations(eventId)?.event ?: return@withTransaction null
        val scopeKey = if (event.musicPoolMode == MusicPoolMode.CUSTOM) {
            TrackSelector.customScope(eventId)
        } else {
            TrackSelector.GLOBAL_SCOPE
        }
        val candidates = if (event.musicPoolMode == MusicPoolMode.CUSTOM) {
            musicDao.getEnabledForEvent(eventId)
        } else {
            musicDao.getEnabledGlobal()
        }.map { it.toDomain() }
        val currentState = musicDao.getShuffleState(scopeKey)?.toDomain()
            ?: ShuffleState(scopeKey = scopeKey)
        val selection = trackSelector.select(candidates, currentState, nowEpochMillis())
        musicDao.upsertShuffleState(selection.nextState.toEntity())
        selection.track
    }

    suspend fun lastPlayedTrackId(eventId: Long): Long? {
        val event = eventDao.getWithRelations(eventId)?.event ?: return null
        val scopeKey = if (event.musicPoolMode == MusicPoolMode.CUSTOM) {
            TrackSelector.customScope(eventId)
        } else {
            TrackSelector.GLOBAL_SCOPE
        }
        return musicDao.getShuffleState(scopeKey)?.lastTrackId
    }
}
