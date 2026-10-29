package com.muyex.randommusicreminder.app

import android.os.SystemClock
import android.util.Log
import com.muyex.randommusicreminder.data.repository.UsageRepository
import com.muyex.randommusicreminder.data.settings.SettingsRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.muyex.randommusicreminder.domain.statistics.UsageChunk
import com.muyex.randommusicreminder.domain.statistics.UsageSessionClock

class UsageTracker(private val repository: UsageRepository, settings: SettingsRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickJob: Job? = null
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val clock = UsageSessionClock(Instant::now, SystemClock::elapsedRealtime, ZoneId::systemDefault, ::save)

    init {
        scope.launch {
            settings.settings.map { it.usageStatsEnabled }.distinctUntilChanged()
                .catch { mutableError.value = "本地统计设置读取失败，本次不采集使用数据"; emit(false) }
                .collect { clock.setEnabled(it); updateTicker() }
        }
    }

    // 只采集乐醒主界面可见区间；Activity 旋转重建不会调用 false，也不会重复计次。
    fun setVisible(value: Boolean) { clock.setVisible(value); updateTicker() }

    private fun updateTicker() {
        if (clock.isActive && tickJob?.isActive != true) {
            tickJob = scope.launch { while (isActive) { delay(15_000); clock.flush() } }
        } else if (!clock.isActive) {
            tickJob?.cancel()
            tickJob = null
        }
    }

    private fun save(chunk: UsageChunk) {
        scope.launch {
            runCatching { repository.record(chunk.start, chunk.elapsedMillis, chunk.launch, chunk.zone) }
                .onFailure {
                    mutableError.value = "本地使用统计保存失败，部分数据可能未记录"
                    Log.w("UsageTracker", "Unable to save local usage", it)
                }
        }
    }
}
