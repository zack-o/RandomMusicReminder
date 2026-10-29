package com.muyex.randommusicreminder.ai

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** 仅供用户明确授权时手动选择；常规测试不联网，不输出密钥和原始响应。 */
class AiLiveDeviceTest {
    @Test fun savedConnectionAndSyntheticDraft() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("lexingLiveAi") == "1")
        val store = AiSettingsStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val config = store.config.value
        val client = AiClient()
        val key = store.secret()
        val answer = client.complete(config, key, "这是最小连接测试。只回复 OK。", "请回复 OK。", forceText = true)
        assertTrue("连接已响应但未按要求返回 OK", answer.contains("OK", ignoreCase = true))
        val response = client.complete(config, key, AiDraftCodec.SYSTEM_PROMPT,
            "当前日期时间2030-01-01T08:00:00+08:00，Asia/Shanghai。请创建单次测试会议，2030年1月3日15:00，提前30分钟和准时提醒；另将2030年1月4日标记为休息日。仅生成草稿。")
        val draft = AiDraftCodec.decode(response)
        assertEquals(1, draft.data.events.size)
        assertEquals(setOf(-30, 0), draft.data.events.single().details.rules.map { it.offsetMinutes }.toSet())
        assertEquals(1, draft.data.days.size)
    }
}
