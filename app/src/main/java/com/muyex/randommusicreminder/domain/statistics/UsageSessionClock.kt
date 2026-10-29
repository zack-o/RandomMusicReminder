package com.muyex.randommusicreminder.domain.statistics

import java.time.Instant
import java.time.ZoneId

data class UsageChunk(val start: Instant, val elapsedMillis: Long, val launch: Boolean, val zone: ZoneId)

class UsageSessionClock(
    private val wallTime: () -> Instant,
    private val elapsedTime: () -> Long,
    private val zone: () -> ZoneId,
    private val record: (UsageChunk) -> Unit,
) {
    private var visible = false
    private var enabled = false
    private var active: Session? = null
    val isActive: Boolean get() = active != null

    fun setVisible(value: Boolean) { visible = value; reconcile() }
    fun setEnabled(value: Boolean) { enabled = value; reconcile() }

    private fun reconcile() {
        if (visible && enabled && active == null) {
            val session = Session(wallTime(), elapsedTime(), zone())
            active = session
            record(UsageChunk(session.start, 0, true, session.zone))
        } else if ((!visible || !enabled) && active != null) {
            flush()
            active = null
        }
    }

    fun flush() {
        val previous = active ?: return
        val elapsed = elapsedTime()
        // 单调时钟只决定持续时长；日期归属单独使用会话片段开始时的墙钟与时区。
        val duration = (elapsed - previous.elapsed).coerceAtLeast(0)
        active = Session(wallTime(), elapsed, zone())
        if (duration > 0) record(UsageChunk(previous.start, duration, false, previous.zone))
    }

    private data class Session(val start: Instant, val elapsed: Long, val zone: ZoneId)
}
