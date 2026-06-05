package com.mozeeb.picoclaw.cmp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 6 PicoClaw theme modes. Colors match picoclaw_fui/lib/src/core/app_theme.dart exactly.
 *
 * | Mode     | Primary (sidebar) | PrimaryContainer (content bg) | Secondary (accent) |
 * |----------|-------------------|-------------------------------|--------------------|
 * | Carbon   | #111111           | #1A1A1A                       | #00E5FF            |
 * | Slate    | #020617           | #0F172A                       | #F59E0B            |
 * | Obsidian | #000000           | #111111                       | #FFFFFF            |
 * | Ebony    | #0C0C0C           | #1A1A1A                       | #FACC15            |
 * | Nord     | #2E3440           | #3B4252                       | #88C0D0            |
 * | Sakura   | #AD1457           | #FDF2F8                       | #C2185B            |
 */
enum class AppThemeMode {
    Carbon,
    Slate,
    Obsidian,
    Ebony,
    Nord,
    Sakura,
}

// ---------------------------------------------------------------------------
// Color palettes
// ---------------------------------------------------------------------------

private val carbonScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1A1A1A),
    onPrimaryContainer = Color(0xFFEEEEEE),
    secondary = Color(0xFF00E5FF),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF00E5FF).copy(alpha = 0.15f),
    onSecondaryContainer = Color(0xFF00E5FF),
    background = Color(0xFF111111),
    onBackground = Color(0xFFEEEEEE),
    surface = Color(0xFF1A1A1A),
    onSurface = Color(0xFFEEEEEE),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF444444),
    error = Color(0xFFCF6679),
)

private val slateScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF020617),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0F172A),
    onPrimaryContainer = Color(0xFFE2E8F0),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFF59E0B).copy(alpha = 0.15f),
    onSecondaryContainer = Color(0xFFF59E0B),
    background = Color(0xFF020617),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    error = Color(0xFFCF6679),
)

private val obsidianScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF111111),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFFFFFFF).copy(alpha = 0.1f),
    onSecondaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF111111),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF333333),
    error = Color(0xFFCF6679),
)

private val ebonyScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF0C0C0C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1A1A1A),
    onPrimaryContainer = Color(0xFFF5F5F5),
    secondary = Color(0xFFFACC15),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFFACC15).copy(alpha = 0.15f),
    onSecondaryContainer = Color(0xFFFACC15),
    background = Color(0xFF0C0C0C),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF1A1A1A),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF404040),
    error = Color(0xFFCF6679),
)

private val nordScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF2E3440),
    onPrimary = Color(0xFFECEFF4),
    primaryContainer = Color(0xFF3B4252),
    onPrimaryContainer = Color(0xFFECEFF4),
    secondary = Color(0xFF88C0D0),
    onSecondary = Color(0xFF2E3440),
    secondaryContainer = Color(0xFF88C0D0).copy(alpha = 0.2f),
    onSecondaryContainer = Color(0xFF88C0D0),
    background = Color(0xFF2E3440),
    onBackground = Color(0xFFECEFF4),
    surface = Color(0xFF3B4252),
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = Color(0xFF434C5E),
    onSurfaceVariant = Color(0xFFD8DEE9),
    outline = Color(0xFF4C566A),
    error = Color(0xFFBF616A),
)

private val sakuraScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFFAD1457),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFDF2F8),
    onPrimaryContainer = Color(0xFF3B0018),
    secondary = Color(0xFFC2185B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD8E4),
    onSecondaryContainer = Color(0xFF3E001F),
    background = Color(0xFFFDF2F8),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFDF2F8),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF8E8F0),
    onSurfaceVariant = Color(0xFF4D4D4D),
    outline = Color(0xFFD6B0C3),
    error = Color(0xFFB3261E),
)

// ---------------------------------------------------------------------------
// Theme function — call at the composable root
// ---------------------------------------------------------------------------

@Composable
fun PicoClawTheme(
    mode: AppThemeMode = AppThemeMode.Carbon,
    content: @Composable () -> Unit,
) {
    val colorScheme = colorSchemeFor(mode)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(), // Uses default Material3 typography; swap for Inter when font bundled
        content = content,
    )
}

fun colorSchemeFor(mode: AppThemeMode): ColorScheme = when (mode) {
    AppThemeMode.Carbon -> carbonScheme
    AppThemeMode.Slate -> slateScheme
    AppThemeMode.Obsidian -> obsidianScheme
    AppThemeMode.Ebony -> ebonyScheme
    AppThemeMode.Nord -> nordScheme
    AppThemeMode.Sakura -> sakuraScheme
}

/** Returns true for all dark themes. Sakura is the only light theme. */
fun AppThemeMode.isDark(): Boolean = this != AppThemeMode.Sakura
