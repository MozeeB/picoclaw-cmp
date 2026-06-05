package com.mozeeb.picoclaw.cmp.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mozeeb.picoclaw.cmp.mvi.ServiceIntent
import com.mozeeb.picoclaw.cmp.mvi.ServiceState
import com.mozeeb.picoclaw.cmp.mvi.ServiceStatus

/**
 * WebView page — embeds the PicoClaw web UI in a platform WebView.
 * Shows a placeholder when the service is stopped.
 *
 * Platform-specific WebView implementations are handled by [PlatformWebView].
 */
@Composable
fun WebViewPage(
    state: ServiceState,
    onIntent: (ServiceIntent) -> Unit,
) {
    if (state.status != ServiceStatus.Running) {
        StoppedPlaceholder(onStartService = { onIntent(ServiceIntent.StartService) })
    } else {
        // Embedded WebView always targets loopback (cleartext-permitted on Android)
        PlatformWebView(url = state.localWebUrl, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun StoppedPlaceholder(onStartService: () -> Unit) {
    val s = com.mozeeb.picoclaw.cmp.i18n.LocalStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = s.serviceNotRunning,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = s.webViewHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onStartService) {
                Text(
                    s.startService,
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
