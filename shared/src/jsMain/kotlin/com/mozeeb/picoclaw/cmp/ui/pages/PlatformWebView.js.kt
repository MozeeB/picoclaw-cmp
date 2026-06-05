package com.mozeeb.picoclaw.cmp.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        TextButton(onClick = { kotlinx.browser.window.open(url, "_blank") }) {
            Text("Open: $url", color = MaterialTheme.colorScheme.secondary)
        }
    }
}
