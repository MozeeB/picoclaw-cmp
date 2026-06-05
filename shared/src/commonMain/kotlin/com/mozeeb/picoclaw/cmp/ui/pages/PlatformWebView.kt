package com.mozeeb.picoclaw.cmp.ui.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific WebView implementation.
 * - Android: android.webkit.WebView via AndroidView
 * - Desktop (JVM): opens URL in the default browser (CEF integration is a Phase 4 enhancement)
 * - iOS: WKWebView (Phase 4)
 * - Web: <iframe> or browser navigation (Phase 4)
 */
@Composable
expect fun PlatformWebView(url: String, modifier: Modifier = Modifier)
