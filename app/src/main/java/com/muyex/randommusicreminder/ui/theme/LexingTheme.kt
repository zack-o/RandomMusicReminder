package com.muyex.randommusicreminder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.muyex.randommusicreminder.domain.settings.*

private val LightColors = lightColorScheme(
    primary = Color(0xFF356859),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F0D8),
    onPrimaryContainer = Color(0xFF052019),
    secondary = Color(0xFF54645D),
    secondaryContainer = Color(0xFFD7E8DF),
    tertiary = Color(0xFF755B2B),
    tertiaryContainer = Color(0xFFFFDEA4),
    background = Color(0xFFF5F7FC),
    surface = Color(0xFFF5F7FC),
    surfaceVariant = Color(0xFFDEE5F0),
    onSurface = Color(0xFF1C2538),
    onSurfaceVariant = Color(0xFF626C80),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEDF1F9),
    surfaceContainerHigh = Color(0xFFE5EBF5),
    surfaceContainerHighest = Color(0xFFDEE5F0),
    outline = Color(0xFF7A869D),
    outlineVariant = Color(0xFFD4DDEB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CD4BC),
    onPrimary = Color(0xFF07382C),
    primaryContainer = Color(0xFF1E5043),
    onPrimaryContainer = Color(0xFFB8F0D8),
    secondary = Color(0xFFBBCBC2),
    tertiary = Color(0xFFE5C17C),
    background = Color(0xFF121722),
    surface = Color(0xFF121722),
    surfaceContainerLowest = Color(0xFF0C101A),
    surfaceContainerLow = Color(0xFF192130),
    surfaceContainer = Color(0xFF20293A),
    surfaceContainerHigh = Color(0xFF293449),
    surfaceContainerHighest = Color(0xFF35415A),
    surfaceVariant = Color(0xFF424E66),
    onSurface = Color(0xFFE4EAF7),
    onSurfaceVariant = Color(0xFFBAC6DE),
    secondaryContainer = Color(0xFF30463B),
    onSecondaryContainer = Color(0xFFD7E8DF),
    outlineVariant = Color(0xFF424E66),
)

private val LexingTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 19.sp),
)

@Composable
fun LexingTheme(appearance: AppearanceSettings = AppearanceSettings(), content: @Composable () -> Unit) {
    val dark = when (appearance.mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val accent = Color(0xFF000000.toInt() or appearance.rgb)
    val primaryRgb = themeAccentRgb(appearance.rgb, dark)
    val primary = Color(0xFF000000.toInt() or primaryRgb)
    val colors = (if (dark) DarkColors else LightColors).copy(primary = primary, onPrimary = if (accentUsesWhite(primaryRgb)) Color.White else Color.Black,
        primaryContainer = lerp(accent, if (dark) Color.Black else Color.White, if (dark) .55f else .86f),
        onPrimaryContainer = if (dark) Color(0xFFF1F5F3) else Color(0xFF18211D),
        secondaryContainer = lerp(accent, if (dark) Color.Black else Color.White, if (dark) .65f else .92f),
        onSecondaryContainer = if (dark) Color(0xFFF1F5F3) else Color(0xFF18211D))
    MaterialTheme(
        colorScheme = colors,
        typography = LexingTypography,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
        content = content,
    )
}
