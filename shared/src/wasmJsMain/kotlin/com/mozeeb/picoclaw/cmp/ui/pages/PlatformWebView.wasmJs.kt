package com.mozeeb.picoclaw.cmp.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
    println("WARN: PlatformWebView: WasmJS target — URL: $url")
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("WebView: $url", color = MaterialTheme.colorScheme.secondary)
    }
}
