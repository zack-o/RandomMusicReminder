package com.muyex.randommusicreminder.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import com.muyex.randommusicreminder.ai.AiConfig
import com.muyex.randommusicreminder.ui.screens.AiScreen
import com.muyex.randommusicreminder.ui.screens.ReminderScreen
import com.muyex.randommusicreminder.ui.theme.LexingTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AiScreenIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private fun content(state: ToolsState = ToolsState(), onSave: (AiConfig, String) -> Unit = { _, _ -> }, onPrepare: (AiAction, String) -> Unit = { _, _ -> }) {
        compose.setContent { LexingTheme { AiScreen(state, onSave, {}, onPrepare, {}, {}, {}, {}, {}) } }
    }
    @Test fun deepSeekPresetKeepsExistingKeyAndPreviewsResolvedAddress() {
        var saved: AiConfig? = null
        var key = "not-saved"
        content(ToolsState(config = AiConfig(true, "https://api.deepseek.com", "old-model"), hasKey = true), onSave = { config, replacement -> saved = config; key = replacement })
        compose.onNodeWithText("管理连接").performClick()
        compose.onNodeWithText("DeepSeek 预设").performClick()
        compose.onNodeWithText("deepseek-flash").assertExists()
        compose.onNodeWithText("实际请求\nhttps://api.deepseek.com/chat/completions").assertExists()
        compose.onNodeWithText("保存配置").performClick()
        compose.runOnIdle {
            assertEquals("https://api.deepseek.com/chat/completions", saved?.requestEndpoint)
            assertEquals("", key)
            assertEquals(false, saved?.completionLimitField)
        }
    }
    @Test fun examplesOnlyFillInputUntilExplicitGeneration() {
        var count = 0
        val state = ToolsState(config = AiConfig(true, "https://api.deepseek.com", "deepseek-flash"), hasKey = true)
        content(state, onPrepare = { action, input -> assertEquals(AiAction.TASKS, action); assertTrue(input.contains("提前30分钟")); count++ })
        compose.onNodeWithText("多次提醒").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, count) }
        compose.onNodeWithText("生成待核对草稿").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, count) }
    }
    @Test fun connectionFailureRemainsVisible() {
        content(ToolsState(aiFeedback = AiFeedback(false, "测试连接未完成", "HTTP 402 · 账户余额不足", 1250)))
        compose.onNodeWithText("HTTP 402 · 账户余额不足").assertExists()
        compose.onNodeWithText("1.3 秒").assertExists()
    }
    @Test fun reminderEmptyStateKeepsToolEntryInSettings() {
        compose.setContent { LexingTheme { ReminderScreen(emptyList(), {}, {}, { _, _ -> }, {}) } }
        compose.onNodeWithText("创建第一个提醒").assertExists()
        compose.onNodeWithText("用一句话安排").assertDoesNotExist()
    }
}
