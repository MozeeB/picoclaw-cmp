package com.mozeeb.picoclaw.cmp.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.net.URI

/**
 * Desktop WebView — opens the URL in the system default browser.
 * Phase 4 enhancement: integrate CEF (Chromium Embedded Framework) for in-app WebView.
 */
@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
    // Auto-open in browser when URL changes
    LaunchedEffect(url) {
        openInBrowser(url)
    }

    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = { openInBrowser(url) }) {
            Text(
                "Open in browser: $url",
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

private fun openInBrowser(url: String) {
    try {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        }
    } catch (e: Exception) {
        println("WARN: Could not open browser for $url: ${e.message}")
    }
}
