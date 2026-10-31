package ru.zamery

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Токены из DESIGN.md (дизайн 0.2 по макету ChatGPT). Цвета в коде — только отсюда. */
data class Tokens(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val chip: Color,        // подложка переключателей и полей
    val text: Color,
    val muted: Color,
    val primary: Color,
    val onPrimary: Color,
    val primarySoft: Color, // светлая зелёная подложка
    val down: Color,        // похудел — хорошо
    val up: Color,          // прибавил
    val line: Color,        // сетка, рамки, разделители
    val danger: Color,
)

val LightTokens = Tokens(
    dark = false,
    bg = Color(0xFFF3F6F7), surface = Color(0xFFFFFFFF), chip = Color(0xFFE9EFF2),
    text = Color(0xFF11181D), muted = Color(0xFF5D6A72),
    primary = Color(0xFF258654), onPrimary = Color.White, primarySoft = Color(0xFFE3F2E9),
    down = Color(0xFF1F8A50), up = Color(0xFFB0432B),
    line = Color(0xFFE1E7EA), danger = Color(0xFFB3261E),
)

val DarkTokens = Tokens(
    dark = true,
    bg = Color(0xFF0C151B), surface = Color(0xFF16212A), chip = Color(0xFF1E2A33),
    text = Color(0xFFE9EEF1), muted = Color(0xFF9AA8B1),
    primary = Color(0xFF57B178), onPrimary = Color(0xFF05200F), primarySoft = Color(0xFF173726),
    down = Color(0xFF62C287), up = Color(0xFFF08A6E),
    line = Color(0xFF26333C), danger = Color(0xFFF2B8B5),
)

val LocalTokens = staticCompositionLocalOf { LightTokens }

@Composable
fun ZameryTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val t = if (dark) DarkTokens else LightTokens
    val scheme = if (dark) darkColorScheme(
        primary = t.primary, onPrimary = t.onPrimary, background = t.bg, surface = t.surface,
        onBackground = t.text, onSurface = t.text, onSurfaceVariant = t.muted,
        surfaceVariant = t.chip, outline = t.muted, outlineVariant = t.line, error = t.danger,
        surfaceContainerHigh = t.surface, surfaceContainer = t.surface, surfaceContainerHighest = t.surface,
        secondaryContainer = t.primarySoft,
    ) else lightColorScheme(
        primary = t.primary, onPrimary = t.onPrimary, background = t.bg, surface = t.surface,
        onBackground = t.text, onSurface = t.text, onSurfaceVariant = t.muted,
        surfaceVariant = t.chip, outline = t.muted, outlineVariant = t.line, error = t.danger,
        surfaceContainerHigh = t.surface, surfaceContainer = t.surface, surfaceContainerHighest = t.surface,
        secondaryContainer = t.primarySoft,
    )
    CompositionLocalProvider(LocalTokens provides t) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
