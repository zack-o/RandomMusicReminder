package com.muyex.randommusicreminder.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.muyex.randommusicreminder.domain.haptics.*
import com.muyex.randommusicreminder.ui.components.OutputOptions
import com.muyex.randommusicreminder.ui.theme.LexingTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HapticOptionsIntegrationTest {
    @get:Rule val compose=createComposeRule()
    @Test fun choosingVibrationExposesParametersWithoutStartingHardware() {
        var chosen=ReminderOutputMode.MUSIC
        compose.setContent {
            var mode by remember { mutableStateOf(ReminderOutputMode.MUSIC) }
            var settings by remember { mutableStateOf(HapticSettings()) }
            LexingTheme { OutputOptions(mode,settings) { next,value -> mode=next;settings=value;chosen=next } }
        }
        compose.onNodeWithText("仅震动").performClick()
        compose.onNodeWithText("使用音乐生成的节奏").assertExists()
        compose.runOnIdle { assertEquals(ReminderOutputMode.VIBRATION,chosen) }
    }
}
