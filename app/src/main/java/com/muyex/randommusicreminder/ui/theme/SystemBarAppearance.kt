package com.muyex.randommusicreminder.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.muyex.randommusicreminder.domain.settings.AppearanceSettings
import com.muyex.randommusicreminder.domain.settings.ThemeMode

@Composable
fun SystemBarAppearance(activity: ComponentActivity, appearance: AppearanceSettings) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (appearance.mode) { ThemeMode.SYSTEM -> systemDark; ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    LaunchedEffect(activity, dark) {
        // 明确使用应用的模式，而非 enableEdgeToEdge 默认仅检测系统主题，避免强制深色仍显示黑色图标。
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
