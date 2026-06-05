package com.mozeeb.picoclaw.cmp.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// TODO Phase 4: Implement using WKWebView via UIKitView
@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
    println("WARN: PlatformWebView not yet implemented on iOS — URL: $url")
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("WebView not yet available on iOS.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
