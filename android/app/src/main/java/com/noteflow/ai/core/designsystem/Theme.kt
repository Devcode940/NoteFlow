package com.noteflow.ai.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.noteflow.ai.domain.model.ThemeMode

/**
 * NoteFlow design system: an Obsidian-inspired, minimal palette built around
 * a violet primary. Light and dark schemes are hand-tuned for contrast
 * (WCAG AA for body text) instead of relying on dynamic color, so the app
 * looks identical on every device.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF4C1D95),
    secondary = Color(0xFF5B5BD6),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF312E81),
    tertiary = Color(0xFF0F766E),
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFCFCFD),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFCFCFD),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC4B5FD),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = Color(0xFFB3C5FF),
    onSecondary = Color(0xFF1B2F6B),
    secondaryContainer = Color(0xFF33437A),
    onSecondaryContainer = Color(0xFFDCE2FF),
    tertiary = Color(0xFF4FD8C4),
    onTertiary = Color(0xFF00382F),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F)
)

@Composable
fun NoteFlowTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = NoteFlowTypography,
        content = content
    )
}

/** Parses a "#RRGGBB" folder color; falls back to the primary color. */
fun parseFolderColor(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    val value = cleaned.toLongOrNull(16) ?: return Color(0xFF6D28D9)
    return if (cleaned.length == 6) {
        Color(0xFF000000 or value)
    } else {
        Color(value)
    }
}

/** Curated folder color palette offered in the folder editor. */
val FolderColorPalette: List<String> = listOf(
    "#7c3aed", "#3b82f6", "#10b981", "#f59e0b",
    "#ef4444", "#ec4899", "#06b6d4", "#64748b"
)

/** Emoji icons offered for folders. */
val FolderIconPalette: List<String> = listOf(
    "📁", "📋", "💼", "🏠", "💡", "📚", "🎯", "🚀",
    "💻", "🎨", "📊", "🧪", "🌱", "⭐", "🔥", "📌"
)
