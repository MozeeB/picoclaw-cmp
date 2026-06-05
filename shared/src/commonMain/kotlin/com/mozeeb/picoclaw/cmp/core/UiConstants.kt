package com.mozeeb.picoclaw.cmp.core

import androidx.compose.ui.unit.dp

/**
 * Layout constants used across all pages.
 * All values are in dp. Extract new constants here rather than hardcoding in composables.
 */
object UiConstants {
    // Navigation
    val navRailWidth = 72.dp
    val navIconSize = 24.dp
    val navItemPadding = 4.dp
    val navLogoSize = 32.dp

    // Touch targets
    val minTouchTarget = 48.dp

    // Cards / containers
    val cardRadius = 16.dp
    val heroCardRadius = 24.dp
    val cardPadding = 20.dp
    val cardPaddingSmall = 12.dp
    val sectionSpacing = 24.dp
    val itemSpacing = 12.dp
    val contentPadding = 20.dp

    // Compact vs wide breakpoint
    val compactBreakpoint = 600.dp

    // Dashboard
    val qrCodeSize = 200.dp
    val statusIconSize = 64.dp
    val statusGlowSize = 88.dp
    val controlButtonHeight = 52.dp

    // Config
    val labelWidth = 120.dp
    val themeSwatchSize = 56.dp
}
