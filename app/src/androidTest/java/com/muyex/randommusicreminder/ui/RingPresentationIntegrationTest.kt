package com.muyex.randommusicreminder.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.muyex.randommusicreminder.alarm.ReminderIntensity
import com.muyex.randommusicreminder.domain.settings.*
import com.muyex.randommusicreminder.music.*
import com.muyex.randommusicreminder.playback.*
import com.muyex.randommusicreminder.ui.theme.LexingTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RingPresentationIntegrationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun coverLyricsAndStopRemainVisibleWithoutStartingPlayer() {
        var stopped = false
        val artwork = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff245f96.toInt()) }
        val metadata = EmbeddedMusicMetadata("本地音乐示例", "离线标签", artwork = artwork, lyrics = Lyrics.parse("[00:00]让音乐提醒重要时刻\n[00:12]现在该出发了\n[00:20]下一句歌词"))
        compose.setContent { LexingTheme { Surface(Modifier.fillMaxSize()) { RingContent(RingScreenState(title = "项目评审", intensity = ReminderIntensity.STRONG), metadata, "示例", 12500, { stopped = true }, {}) } } }
        compose.onNodeWithContentDescription("内嵌专辑封面").assertIsDisplayed()
        compose.onNodeWithText("现在该出发了").assertExists()
        compose.onNodeWithText("停止提醒").assertIsDisplayed()
        compose.waitForIdle(); captureUi("ring-lyrics")
        compose.onNodeWithText("停止提醒").performClick()
        compose.runOnIdle { assertTrue(stopped) }
    }
    @Test fun missingTagsAndDisabledSnoozeDoNotHideStopInDarkTheme() {
        compose.setContent { LexingTheme(AppearanceSettings(mode = ThemeMode.DARK)) { Surface(Modifier.fillMaxSize()) { RingContent(RingScreenState(title = "温和提醒", snoozeMinutes = null), EmbeddedMusicMetadata(), "兜底铃声", 0, {}, {}) } } }
        compose.onNodeWithText("未找到支持的内嵌歌词").assertExists()
        compose.onNodeWithText("停止提醒").assertIsDisplayed()
        compose.onNodeWithText("10分钟").assertDoesNotExist()
        compose.waitForIdle(); captureUi("ring-dark-fallback", expectDark = true)
    }
}
