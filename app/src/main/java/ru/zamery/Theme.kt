package ru.zamery

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Токены из DESIGN.md. Цвета в коде — только отсюда. */
data class Tokens(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val onPrimary: Color,
    val down: Color,   // похудел — хорошо
    val up: Color,     // прибавил
    val line: Color,   // сетка графика, разделители
    val danger: Color,
)

val LightTokens = Tokens(
    bg = Color(0xFFF6F7F5), surface = Color(0xFFFFFFFF),
    text = Color(0xFF1B1F1D), muted = Color(0xFF55605B),
    primary = Color(0xFF1E7A55), onPrimary = Color.White,
    down = Color(0xFF1E7A55), up = Color(0xFFB0432B),
    line = Color(0xFFE1E5E2), danger = Color(0xFFB3261E),
)

val DarkTokens = Tokens(
    bg = Color(0xFF121413), surface = Color(0xFF1C1F1E),
    text = Color(0xFFE8ECEA), muted = Color(0xFFA6B0AB),
    primary = Color(0xFF5CC39A), onPrimary = Color(0xFF06261A),
    down = Color(0xFF5CC39A), up = Color(0xFFF08A6E),
    line = Color(0xFF2E3331), danger = Color(0xFFF2B8B5),
)

val LocalTokens = staticCompositionLocalOf { LightTokens }

@Composable
fun ZameryTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val t = if (dark) DarkTokens else LightTokens
    val scheme = if (dark) darkColorScheme(
        primary = t.primary, onPrimary = t.onPrimary, background = t.bg, surface = t.surface,
        onBackground = t.text, onSurface = t.text, onSurfaceVariant = t.muted,
        surfaceVariant = t.line, outline = t.muted, error = t.danger,
        surfaceContainerHigh = t.surface, surfaceContainer = t.surface,
    ) else lightColorScheme(
        primary = t.primary, onPrimary = t.onPrimary, background = t.bg, surface = t.surface,
        onBackground = t.text, onSurface = t.text, onSurfaceVariant = t.muted,
        surfaceVariant = t.line, outline = t.muted, error = t.danger,
        surfaceContainerHigh = t.surface, surfaceContainer = t.surface,
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalTokens provides t) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
