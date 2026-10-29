package com.muyex.randommusicreminder.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.muyex.randommusicreminder.domain.settings.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.muyex.randommusicreminder.domain.music.PreviewOptions

private val Context.settingsDataStore by preferencesDataStore(name = "lexing_settings")

data class AppSettings(
    val gentleDurationSeconds: Int = 30,
    val strongDurationSeconds: Int = 60,
    val defaultSnoozeMinutes: Int = 10,
    val maxSnoozeCount: Int = 3,
    val notifyMissedReminders: Boolean = true,
    val previewOptions: PreviewOptions = PreviewOptions(),
    val usageStatsEnabled: Boolean = true,
    val appearance: AppearanceSettings = AppearanceSettings(),
)

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { values ->
        AppSettings(
            gentleDurationSeconds = values[GENTLE_DURATION] ?: 30,
            strongDurationSeconds = values[STRONG_DURATION] ?: 60,
            defaultSnoozeMinutes = values[DEFAULT_SNOOZE] ?: 10,
            maxSnoozeCount = values[MAX_SNOOZE_COUNT] ?: 3,
            notifyMissedReminders = values[NOTIFY_MISSED] ?: true,
            previewOptions = PreviewOptions(
                durationSeconds = (values[PREVIEW_DURATION] ?: 5).coerceIn(1, 300),
                volumePercent = (values[PREVIEW_VOLUME] ?: 5).coerceIn(0, 100),
                startSeconds = (values[PREVIEW_START] ?: 0).coerceIn(0, 86_400),
            ),
            usageStatsEnabled = values[USAGE_STATS_ENABLED] ?: true,
            appearance = runCatching { AppearanceSettings(ThemeMode.valueOf(values[THEME_MODE] ?: "SYSTEM"), ThemePalette.valueOf(values[THEME_PALETTE] ?: "DAWN"), values[THEME_COLOR] ?: "#356859") }.getOrDefault(AppearanceSettings()),
        )
    }

    suspend fun updateGentleDuration(seconds: Int) {
        context.settingsDataStore.edit { it[GENTLE_DURATION] = seconds.coerceIn(5, 1_800) }
    }

    suspend fun updateStrongDuration(seconds: Int) {
        context.settingsDataStore.edit { it[STRONG_DURATION] = seconds.coerceIn(5, 1_800) }
    }

    suspend fun updateDefaultSnooze(minutes: Int) {
        val allowed = listOf(5, 10, 15)
        val nearest = allowed.minBy { kotlin.math.abs(it - minutes) }
        context.settingsDataStore.edit { it[DEFAULT_SNOOZE] = nearest }
    }

    suspend fun updateNotifyMissedReminders(enabled: Boolean) {
        context.settingsDataStore.edit { it[NOTIFY_MISSED] = enabled }
    }

    suspend fun updatePreviewOptions(options: PreviewOptions) {
        context.settingsDataStore.edit {
            it[PREVIEW_DURATION] = options.durationSeconds
            it[PREVIEW_VOLUME] = options.volumePercent
            it[PREVIEW_START] = options.startSeconds
        }
    }

    suspend fun updateUsageStatsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[USAGE_STATS_ENABLED] = enabled }
    }

    suspend fun updateAppearance(value: AppearanceSettings) {
        context.settingsDataStore.edit { it[THEME_MODE] = value.mode.name; it[THEME_PALETTE] = value.palette.name; it[THEME_COLOR] = value.customColor.uppercase() }
    }

    suspend fun applyImported(settings: AppSettings) {
        require(settings.gentleDurationSeconds in 5..1800 && settings.strongDurationSeconds in 5..1800)
        require(settings.defaultSnoozeMinutes in setOf(5, 10, 15) && settings.maxSnoozeCount == 3)
        context.settingsDataStore.edit {
            it[GENTLE_DURATION] = settings.gentleDurationSeconds
            it[STRONG_DURATION] = settings.strongDurationSeconds
            it[DEFAULT_SNOOZE] = settings.defaultSnoozeMinutes
            it[NOTIFY_MISSED] = settings.notifyMissedReminders
            it[PREVIEW_DURATION] = settings.previewOptions.durationSeconds
            it[PREVIEW_VOLUME] = settings.previewOptions.volumePercent
            it[PREVIEW_START] = settings.previewOptions.startSeconds
            it[USAGE_STATS_ENABLED] = settings.usageStatsEnabled
            it[THEME_MODE] = settings.appearance.mode.name
            it[THEME_PALETTE] = settings.appearance.palette.name
            it[THEME_COLOR] = settings.appearance.customColor.uppercase()
        }
    }

    private companion object {
        val GENTLE_DURATION = intPreferencesKey("gentle_duration_seconds")
        val STRONG_DURATION = intPreferencesKey("strong_duration_seconds")
        val DEFAULT_SNOOZE = intPreferencesKey("default_snooze_minutes")
        val MAX_SNOOZE_COUNT = intPreferencesKey("max_snooze_count")
        val NOTIFY_MISSED = booleanPreferencesKey("notify_missed_reminders")
        val PREVIEW_DURATION = intPreferencesKey("preview_duration_seconds")
        val PREVIEW_VOLUME = intPreferencesKey("preview_volume_percent")
        val PREVIEW_START = intPreferencesKey("preview_start_seconds")
        val USAGE_STATS_ENABLED = booleanPreferencesKey("usage_stats_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val THEME_COLOR = stringPreferencesKey("theme_custom_color")
    }
}
