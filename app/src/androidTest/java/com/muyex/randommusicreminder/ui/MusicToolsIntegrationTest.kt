package com.muyex.randommusicreminder.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.material3.MaterialTheme
import com.muyex.randommusicreminder.domain.music.PreviewOptions
import com.muyex.randommusicreminder.ui.screens.MusicScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MusicToolsIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val tracks = listOf(
        MusicListItem(1, "Forest Rain", "", true, true),
        MusicListItem(2, "Piano", "", false, true),
    )

    private fun content(items: List<MusicListItem> = tracks, onBulk: (List<Long>, Boolean) -> Unit = { _, _ -> }) {
        compose.setContent {
            MaterialTheme {
                MusicScreen(items, null, false, {}, {}, {}, { _, _ -> }, {}, PreviewOptions(), {}, onBulk, null, {})
            }
        }
    }

    @Test fun searchOnlyChangesVisibleItems() {
        content()
        compose.onNodeWithText("搜索歌曲名称，空格分隔关键词").performTextInput("forest")
        compose.onNodeWithText("Piano").assertDoesNotExist()
        compose.onNodeWithText("显示 1 / 2 首").assertExists()
    }

    @Test fun batchToggleOnlyReceivesExplicitSelection() {
        var received = emptyList<Long>()
        var enabled = false
        content(onBulk = { ids, value -> received = ids; enabled = value })
        compose.onNodeWithText("批量选择").performClick()
        compose.onNodeWithContentDescription("选择Forest Rain").performClick()
        compose.onNodeWithText("启用所选").performClick()
        compose.runOnIdle { assertEquals(listOf(1L), received); assertEquals(true, enabled) }
    }

    @Test fun invalidTrackCannotBePreviewed() {
        content(listOf(MusicListItem(3, "失效音乐", "文件损坏", true, false)))
        compose.onNodeWithContentDescription("试听").assertIsNotEnabled()
    }
}
