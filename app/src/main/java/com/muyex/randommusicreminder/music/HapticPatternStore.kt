package com.muyex.randommusicreminder.music

import android.content.Context
import androidx.core.net.toUri
import android.util.AtomicFile
import com.muyex.randommusicreminder.domain.haptics.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CachedHapticRhythm(val pattern: RhythmPattern,val startSeconds: Int,val lengthSeconds: Int)

class HapticPatternStore(context: Context) {
    private val app=context.applicationContext
    private val directory=File(app.filesDir,"haptic-rhythms-v1")
    private fun cache(trackId: String): File {
        require(trackId.toLongOrNull()?.let { it>0 } == true) { "曲目标识无效" }
        return File(directory,"$trackId.json")
    }
    private fun source(uri: String): File {
        val parsed=uri.toUri();require(parsed.scheme=="file") { "只分析已导入的本机音乐副本" }
        val root=File(app.filesDir,MusicImporter.DIRECTORY_NAME).canonicalFile
        val file=File(requireNotNull(parsed.path)).canonicalFile
        require(file.parentFile==root && file.isFile) { "音乐副本已移除或不在应用私有目录" }
        return file
    }
    private fun identity(file: File)="${file.name}:${file.length()}:${file.lastModified()}"

    suspend fun read(trackId: String, uri: String): CachedHapticRhythm? = withContext(Dispatchers.IO) {
        runCatching {
            val file=source(uri);val saved=cache(trackId)
            require(saved.isFile && saved.length() in 1..65536)
            val json=JSONObject(saved.readText(Charsets.UTF_8))
            require(json.getInt("version")==EnergyRhythm.ALGORITHM_VERSION && json.getString("source")==identity(file))
            val array=json.getJSONArray("pulses");require(array.length() in 1..200)
            val pattern=RhythmPattern(json.getInt("durationMillis"),(0 until array.length()).map { i ->
                val pulse=array.getJSONArray(i);HapticPulse(pulse.getInt(0),pulse.getInt(1),pulse.getInt(2))
            }).also { it.validate() }
            val start=json.getInt("startSeconds");val length=json.getInt("lengthSeconds")
            require(start in 0..86400 && length in 10..30 && pattern.durationMillis<=length*1000)
            CachedHapticRhythm(pattern,start,length)
        }.getOrNull()
    }

    suspend fun generate(trackId: String, uri: String, startSeconds: Int, lengthSeconds: Int): CachedHapticRhythm = generationMutex.withLock {
        withContext(Dispatchers.Default) {
            val file=source(uri);val before=identity(file)
            val pattern=PcmRhythmAnalyzer().analyze(file,startSeconds,lengthSeconds)
            currentCoroutineContext().ensureActive();require(file.isFile && identity(file)==before) { "分析期间歌曲发生变化，请重试" }
            val json=JSONObject().put("version",EnergyRhythm.ALGORITHM_VERSION).put("source",before)
                .put("startSeconds",startSeconds).put("lengthSeconds",lengthSeconds).put("durationMillis",pattern.durationMillis)
                .put("pulses",JSONArray().apply { pattern.pulses.forEach { put(JSONArray(listOf(it.startMillis,it.durationMillis,it.amplitude))) } })
            withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive();check(directory.isDirectory || directory.mkdirs())
                val atomic=AtomicFile(cache(trackId));val output=atomic.startWrite()
                try { output.write(json.toString().toByteArray(Charsets.UTF_8));currentCoroutineContext().ensureActive();atomic.finishWrite(output) }
                catch(error: Throwable) { atomic.failWrite(output);throw error }
            }
            CachedHapticRhythm(pattern,startSeconds,lengthSeconds)
        }
    }

    companion object { private val generationMutex=Mutex() }
}
