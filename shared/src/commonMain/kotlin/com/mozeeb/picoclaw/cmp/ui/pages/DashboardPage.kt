package com.mozeeb.picoclaw.cmp.ui.pages

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mozeeb.picoclaw.cmp.core.UiConstants
import com.mozeeb.picoclaw.cmp.mvi.ServiceIntent
import com.mozeeb.picoclaw.cmp.mvi.ServiceState
import com.mozeeb.picoclaw.cmp.mvi.ServiceStatus
import com.mozeeb.picoclaw.cmp.ui.widgets.QrCodeImage
import kotlinx.coroutines.launch

@Composable
fun DashboardPage(
    state: ServiceState,
    onIntent: (ServiceIntent) -> Unit,
    /** Called when user taps "Go to Config" from the binary-missing banner. */
    onNavigateToConfig: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(UiConstants.contentPadding),
            verticalArrangement = Arrangement.spacedBy(UiConstants.itemSpacing),
        ) {
            // Page title
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(Modifier.height(4.dp))

            // ── Binary missing banner ─────────────────────────────────────────
            if (state.isBinaryMissing) {
                BinaryMissingBanner(
                    searchedPaths = state.binarySearchedPaths,
                    isDownloadSupported = state.isDownloadSupported,
                    isDownloading = state.isDownloading,
                    downloadProgress = state.downloadProgress,
                    onGoToConfig = onNavigateToConfig,
                    onDownload = { onIntent(ServiceIntent.DownloadBinary) },
                )
            }

            // ── Status hero card ──────────────────────────────────────────────
            StatusHeroCard(
                state = state,
                onStart = { onIntent(ServiceIntent.StartService) },
                onStop = { onIntent(ServiceIntent.StopService) },
            )

            // ── URL chip ─────────────────────────────────────────────────────
            UrlChip(
                url = state.webUrl,
                onCopy = {
                    clipboard.setText(AnnotatedString(state.webUrl))
                    scope.launch { snackbarHostState.showSnackbar("URL copied to clipboard") }
                },
            )

            // ── QR code ───────────────────────────────────────────────────────
            QrCard(url = state.webUrl)

            // ── Public mode ───────────────────────────────────────────────────
            PublicModeCard(
                publicMode = state.publicMode,
                deviceIp = state.deviceIp,
                onToggle = { onIntent(ServiceIntent.TogglePublicMode(it)) },
            )

            // ── Error banner ──────────────────────────────────────────────────
            state.errorMessage?.let { msg ->
                ErrorBanner(
                    message = msg,
                    onDismiss = { onIntent(ServiceIntent.DismissError) },
                )
            }

            Spacer(Modifier.height(32.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Binary missing banner — prominent, actionable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BinaryMissingBanner(
    searchedPaths: List<String>,
    isDownloadSupported: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    onGoToConfig: () -> Unit,
    onDownload: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiConstants.cardRadius),
        colors = CardDefaults.elevatedCardColors(
            containerColor = cs.errorContainer,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                tint = cs.error,
                modifier = Modifier.size(20.dp).padding(top = 2.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Binary not found",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = cs.onErrorContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isDownloadSupported) {
                        "The picoclaw binary was not found. Download it automatically, " +
                            "or set the path manually in Config."
                    } else {
                        "The picoclaw binary was not found in any of the default locations. " +
                            "Set the binary path in Config."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onErrorContainer.copy(alpha = 0.85f),
                )
                if (searchedPaths.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Searched:\n" + searchedPaths.take(4).joinToString("\n") { "• $it" },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        ),
                        color = cs.onErrorContainer.copy(alpha = 0.7f),
                    )
                }

                // Download progress (when downloading)
                if (isDownloading) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = cs.error,
                        trackColor = cs.error.copy(alpha = 0.2f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Downloading… ${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onErrorContainer,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isDownloadSupported) {
                        Button(
                            onClick = onDownload,
                            enabled = !isDownloading,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = cs.error,
                                contentColor = cs.onError,
                            ),
                        ) {
                            Icon(Icons.Filled.CloudDownload, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isDownloading) "Downloading…" else "Download binary",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    TextButton(
                        onClick = onGoToConfig,
                        colors = ButtonDefaults.textButtonColors(contentColor = cs.error),
                    ) {
                        Text("Config →", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Status hero card — animated status indicator + Start/Stop controls
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusHeroCard(
    state: ServiceState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    val statusColor by animateColorAsState(
        targetValue = when (state.status) {
            ServiceStatus.Running  -> cs.secondary
            ServiceStatus.Starting -> cs.secondary.copy(alpha = 0.7f)
            ServiceStatus.Stopping -> cs.error.copy(alpha = 0.7f)
            ServiceStatus.Stopped  -> cs.onSurfaceVariant.copy(alpha = 0.45f)
        },
        label = "hero_status_color",
    )

    // Pulsing glow when Running
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "glow_scale",
    )
    // Rotating ring when Starting / Stopping
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "rotation",
    )

    val isTransitioning = state.status == ServiceStatus.Starting || state.status == ServiceStatus.Stopping
    val isStopped = state.status == ServiceStatus.Stopped
    val isRunning = state.status == ServiceStatus.Running

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiConstants.heroCardRadius),
        colors = CardDefaults.elevatedCardColors(containerColor = cs.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Animated status orb
            Box(contentAlignment = Alignment.Center) {
                // Glow ring (running only)
                if (isRunning) {
                    Box(
                        modifier = Modifier
                            .size(UiConstants.statusGlowSize * glowScale)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.12f)),
                    )
                }
                // Inner circle
                Box(
                    modifier = Modifier
                        .size(UiConstants.statusIconSize)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.18f))
                        .border(
                            width = if (isTransitioning) 2.dp else 0.dp,
                            color = statusColor.copy(alpha = 0.5f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when (state.status) {
                            ServiceStatus.Running  -> "✓"
                            ServiceStatus.Starting -> "…"
                            ServiceStatus.Stopping -> "…"
                            ServiceStatus.Stopped  -> "○"
                        },
                        color = statusColor,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                        ),
                    )
                }
            }

            // Status text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when (state.status) {
                        ServiceStatus.Running  -> "Running"
                        ServiceStatus.Starting -> "Starting…"
                        ServiceStatus.Stopping -> "Stopping…"
                        ServiceStatus.Stopped  -> "Stopped"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    ),
                    color = statusColor,
                )
                if (isRunning) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.webUrl,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = cs.secondary.copy(alpha = 0.75f),
                    )
                }
            }

            // Start / Stop buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // START — primary, filled with accent color
                Button(
                    onClick = onStart,
                    enabled = isStopped,
                    modifier = Modifier
                        .weight(1f)
                        .height(UiConstants.controlButtonHeight),
                    shape = RoundedCornerShape(UiConstants.cardRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.secondary,
                        contentColor = cs.onSecondary,
                        disabledContainerColor = cs.onSurface.copy(alpha = 0.1f),
                        disabledContentColor = cs.onSurface.copy(alpha = 0.3f),
                    ),
                ) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start", style = MaterialTheme.typography.labelLarge)
                }

                // STOP — destructive outlined button
                OutlinedButton(
                    onClick = onStop,
                    enabled = isRunning || isTransitioning,
                    modifier = Modifier
                        .weight(1f)
                        .height(UiConstants.controlButtonHeight),
                    shape = RoundedCornerShape(UiConstants.cardRadius),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.5.dp,
                        color = if (isRunning || isTransitioning) cs.error
                        else cs.outline.copy(alpha = 0.25f),
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = cs.error,
                        disabledContentColor = cs.onSurface.copy(alpha = 0.3f),
                    ),
                ) {
                    Icon(Icons.Filled.Stop, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stop", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// URL chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UrlChip(url: String, onCopy: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiConstants.cardRadius),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                tint = cs.secondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = cs.secondary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .size(UiConstants.minTouchTarget)
                    .semantics { role = Role.Button },
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy URL",
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QR card — white background so QR code is always scannable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QrCard(url: String) {
    val cs = MaterialTheme.colorScheme
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiConstants.cardRadius),
        colors = CardDefaults.elevatedCardColors(containerColor = cs.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Scan to open",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
            // White padded container — guarantees QR scanability regardless of theme
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(16.dp),
            ) {
                QrCodeImage(
                    data = url,
                    modifier = Modifier.size(UiConstants.qrCodeSize),
                    darkColor = androidx.compose.ui.graphics.Color.Black,
                    lightColor = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Public mode toggle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PublicModeCard(
    publicMode: Boolean,
    deviceIp: String?,
    onToggle: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiConstants.cardRadius),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Public Mode",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = cs.onSurface,
                )
                // Status sub-text reflects the actual resolved IP so the user can confirm it works
                val subtitle = when {
                    publicMode && deviceIp != null ->
                        "Reachable on your LAN at $deviceIp"
                    publicMode && deviceIp == null ->
                        "Could not detect a LAN IP — check your network"
                    else ->
                        "Listen on all interfaces; use device LAN IP in QR/URL"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (publicMode && deviceIp != null) cs.secondary else cs.onSurfaceVariant,
                )
            }
            Switch(
                checked = publicMode,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = cs.onSecondary,
                    checkedTrackColor = cs.secondary,
                    uncheckedThumbColor = cs.outline,
                    uncheckedTrackColor = cs.surfaceVariant,
                ),
                modifier = Modifier.semantics {
                    stateDescription = if (publicMode) "enabled" else "disabled"
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Generic error banner with dismiss
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiConstants.cardRadius),
        colors = CardDefaults.cardColors(containerColor = cs.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = cs.error,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = cs.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
