package com.muyex.randommusicreminder.data.transfer

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.local.entity.toEntity
import com.muyex.randommusicreminder.data.repository.*
import com.muyex.randommusicreminder.data.settings.SettingsRepository
import com.muyex.randommusicreminder.domain.calendar.CalendarOverride
import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.domain.transfer.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class ImportPreview(val data: BackupPackage, val events: List<EventDetails>, val days: List<CalendarOverride>, val warnings: List<String>)
data class ImportResult(val events: Int, val days: Int)

class DataTransferService(private val context: Context, private val database: AppDatabase,
    private val events: EventRepository, private val music: MusicRepository,
    private val calendar: CalendarRepository, private val settings: SettingsRepository) {

    suspend fun snapshot(settingsOnly: Boolean = false): BackupPackage {
        val preferences = settings.settings.first()
        if (settingsOnly) return BackupPackage(settings = preferences)
        return database.withTransaction {
            val tracks = music.getTracks().associateBy { it.id }
            BackupPackage(preferences, events.getEvents().map { item ->
                BackupEvent(item, item.customTrackIds.mapNotNull { tracks[it] }.map { MusicReference(it.displayName, it.durationMillis, it.fileSizeBytes) })
            }, calendar.getAll())
        }
    }

    suspend fun export(uri: Uri, settingsOnly: Boolean) {
        val text = BackupCodec.encode(snapshot(settingsOnly))
        withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "无法写入目标文件" }.bufferedWriter(Charsets.UTF_8).use { it.write(text) } }
    }

    suspend fun saveRecovery(settingsOnly: Boolean = false) {
        val before = BackupCodec.encode(snapshot(settingsOnly))
        withContext(Dispatchers.IO) {
            val directory = File(context.filesDir, "import-recovery").apply { check(exists() || mkdirs()) }
            File(directory, "before-${System.currentTimeMillis()}-${UUID.randomUUID()}.json").writeText(before, Charsets.UTF_8)
        }
    }

    suspend fun exportRecovery(uri: Uri) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "import-recovery").listFiles()?.filter { it.isFile && it.name.startsWith("before-") && it.extension == "json" }
            ?.maxByOrNull { it.lastModified() } ?: error("还没有导入前快照")
        require(file.length() <= BackupCodec.MAX_BYTES) { "恢复材料超出大小限制" }
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "无法写入文件" }.use { output -> file.inputStream().use { it.copyTo(output) } }
    }

    suspend fun read(uri: Uri): BackupPackage = withContext(Dispatchers.IO) {
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)) { "无法打开文件" }.use { input ->
            val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                require(output.size() + count <= BackupCodec.MAX_BYTES) { "文件超过 2 MiB" }; output.write(buffer, 0, count)
            }; output.toByteArray()
        }
        BackupCodec.decode(Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString())
    }

    suspend fun prepare(data: BackupPackage): ImportPreview {
        val existing = events.getEventsIncludingDeleted().mapTo(hashSetOf(), TransferRules::fingerprint)
        val tracks = music.getTracks(); val warnings = mutableListOf<String>()
        val candidates = data.events.mapNotNull { item ->
            TransferRules.validateEvent(item.details)
            if (item.details.event.outputMode.audio && item.details.event.musicPoolMode == MusicPoolMode.CUSTOM && item.music.isEmpty()) {
                warnings += "${item.details.event.title}：原自选音乐池已空，整条事件跳过"; return@mapNotNull null
            }
            val refs = if (item.details.event.musicPoolMode == MusicPoolMode.CUSTOM) item.music.map { ref ->
                tracks.filter { it.displayName == ref.name && it.durationMillis == ref.duration && it.fileSizeBytes == ref.bytes }.singleOrNull()?.id
            } else emptyList()
            if (refs.any { it == null } && item.details.event.outputMode.audio) { warnings += "${item.details.event.title}：音乐缺失或同名引用不唯一，整条事件跳过"; null }
            else {
                if(refs.any { it==null }) warnings += "${item.details.event.title}：部分震动节奏曲目未匹配，缺缓存时使用预设，保持静音方式"
                val mapped = item.details.copy(event = item.details.event.copy(id = 0, randomSeed = 0, timingRevision = 0, windowFirstNotBeforeEpochMillis = null), rules = item.details.rules.map { it.copy(id = 0, eventId = 0) }, customTrackIds = refs.filterNotNull().toSet())
                if (!existing.add(TransferRules.fingerprint(mapped))) { warnings += "${mapped.event.title}：与已有事件或本批次重复，跳过"; null } else mapped
            }
        }
        val dates = calendar.getAll().mapTo(hashSetOf()) { it.date }
        val days = data.days.filter { if (!dates.add(it.date)) { warnings += "${it.date}：已有自定义日历，保留现有设置"; false } else true }
        return ImportPreview(data, candidates, days, warnings)
    }

    suspend fun merge(preview: ImportPreview, preserveEnabled: Boolean, source: String = "文件导入（用户确认）"): ImportResult {
        // 先保存只读恢复材料；实际合并只新增，所有已有事件和冲突日期均保留。
        saveRecovery()
        return database.withTransaction {
            val fresh = prepare(preview.data)
            fresh.events.forEach { item -> events.saveEvent(item.copy(event = item.event.copy(enabled = preserveEnabled && item.event.enabled))) }
            database.calendarDao().upsert(fresh.days.map { it.copy(source = source, updatedAt = System.currentTimeMillis()).toEntity() })
            ImportResult(fresh.events.size, fresh.days.size)
        }
    }

    suspend fun exportStatistics(uri: Uri, usage: List<com.muyex.randommusicreminder.domain.statistics.DailyUsage>, history: List<com.muyex.randommusicreminder.domain.ringing.RingHistoryEntry>) = withContext(Dispatchers.IO) {
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "无法写入目标文件" }.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF类型,日期或开始时间,标题,音乐,结果,使用毫秒,打开返回次数,时间窗来源\r\n")
            usage.forEach { writer.write(listOf("使用", it.date.toString(), "", "", "", it.foregroundMillis.toString(), it.launches.toString(), "").joinToString(",", transform = ::csvCell) + "\r\n") }
            history.forEach { writer.write(listOf("响铃-${it.kind}", java.time.Instant.ofEpochMilli(it.startedAtEpochMillis).toString(), it.title, it.musicName.orEmpty(), it.resultLabel, "", "", it.timingSummary.orEmpty()).joinToString(",", transform = ::csvCell) + "\r\n") }
        }
    }

    suspend fun exportTaskRecords(uri:Uri,records:List<com.muyex.randommusicreminder.domain.tasks.TaskActionRecord>)=withContext(Dispatchers.IO) {
        requireNotNull(context.contentResolver.openOutputStream(uri,"wt")).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF操作编号,操作,操作时间,事项,发生日期,发生说明\r\n")
            records.forEach { row -> writer.write(listOf(row.id.toString(),row.action.label,java.time.Instant.ofEpochMilli(row.atEpochMillis).toString(),row.title,row.baseDate.toString(),row.label).joinToString(",",transform=::csvCell)+"\r\n") }
        }
    }
}
