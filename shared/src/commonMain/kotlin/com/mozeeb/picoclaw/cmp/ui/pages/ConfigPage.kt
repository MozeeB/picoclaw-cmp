package com.mozeeb.picoclaw.cmp.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mozeeb.picoclaw.cmp.core.UiConstants
import com.mozeeb.picoclaw.cmp.mvi.ServiceIntent
import com.mozeeb.picoclaw.cmp.mvi.ServiceState
import com.mozeeb.picoclaw.cmp.ui.AppThemeMode
import com.mozeeb.picoclaw.cmp.ui.colorSchemeFor

/**
 * Config page — mirrors picoclaw_fui/lib/src/ui/config_page.dart.
 * Settings: host/port/path, binary path, extra args, autostart,
 * theme picker (6 swatches), language selector (12 locales).
 */
@Composable
fun ConfigPage(
    state: ServiceState,
    onIntent: (ServiceIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(UiConstants.contentPadding)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(UiConstants.itemSpacing),
    ) {
        // Header with save button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "CONFIG",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            if (state.configDirty) {
                IconButton(
                    onClick = { onIntent(ServiceIntent.SaveConfig) },
                    modifier = Modifier
                        .size(UiConstants.minTouchTarget)
                        .semantics { role = Role.Button },
                ) {
                    Icon(
                        Icons.Filled.Save,
                        contentDescription = "Save config",
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Server settings section
        SectionCard(title = "Server") {
            ConfigTextField(
                label = "Host",
                value = state.host,
                onValueChange = { onIntent(ServiceIntent.UpdateHost(it)) },
                placeholder = "127.0.0.1",
            )
            Spacer(Modifier.height(8.dp))
            ConfigTextField(
                label = "Port",
                value = state.port.toString(),
                onValueChange = { v -> v.toIntOrNull()?.let { onIntent(ServiceIntent.UpdatePort(it)) } },
                placeholder = "18800",
            )
            Spacer(Modifier.height(8.dp))
            ConfigTextField(
                label = "Path",
                value = state.path,
                onValueChange = { onIntent(ServiceIntent.UpdatePath(it)) },
                placeholder = "/",
            )
        }

        Spacer(Modifier.height(8.dp))

        // Binary section
        SectionCard(title = "Binary") {
            ConfigTextField(
                label = "Binary path",
                value = state.binaryPath,
                onValueChange = { onIntent(ServiceIntent.UpdateBinaryPath(it)) },
                placeholder = "~/.picoclaw/bin/picoclaw",
            )
            Spacer(Modifier.height(8.dp))

            // Binary status row
            BinaryStatusRow(found = state.binaryFound)

            Spacer(Modifier.height(12.dp))

            // Action buttons: Browse, Validate, Download
            BinaryActions(
                isDownloadSupported = state.isDownloadSupported,
                isDownloading = state.isDownloading,
                onBrowse = { onIntent(ServiceIntent.PickBinaryFile) },
                onValidate = { onIntent(ServiceIntent.ValidateBinary) },
                onDownload = { onIntent(ServiceIntent.DownloadBinary) },
            )

            // Download progress bar
            if (state.isDownloading) {
                Spacer(Modifier.height(12.dp))
                DownloadProgress(progress = state.downloadProgress)
            }

            Spacer(Modifier.height(12.dp))
            ConfigTextField(
                label = "Extra args",
                value = state.extraArgs,
                onValueChange = { onIntent(ServiceIntent.UpdateExtraArgs(it)) },
                placeholder = "--verbose",
            )
            Spacer(Modifier.height(8.dp))
            ConfigSwitchRow(
                label = "Auto-start on launch",
                checked = state.autoStart,
                onCheckedChange = { onIntent(ServiceIntent.ToggleAutoStart(it)) },
            )
        }

        Spacer(Modifier.height(8.dp))

        // Theme section
        SectionCard(title = "Theme") {
            ThemePicker(
                selected = state.theme,
                onSelect = { onIntent(ServiceIntent.SelectTheme(it)) },
            )
        }

        Spacer(Modifier.height(8.dp))

        // Language section
        SectionCard(title = "Language") {
            LanguagePicker(
                selected = state.locale,
                onSelect = { onIntent(ServiceIntent.SelectLocale(it)) },
            )
        }

        Spacer(Modifier.height(8.dp))

        // Telemetry
        SectionCard(title = "Telemetry") {
            ConfigSwitchRow(
                label = "Enable analytics",
                description = "Help improve PicoClaw by sending anonymous usage data.",
                checked = state.isTelemetryEnabled,
                onCheckedChange = { onIntent(ServiceIntent.ToggleTelemetry(it)) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Section card
// ---------------------------------------------------------------------------

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiConstants.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(UiConstants.cardPadding)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Binary status + actions (Browse / Validate / Download)
// ---------------------------------------------------------------------------

@Composable
private fun BinaryStatusRow(found: Boolean?) {
    val cs = MaterialTheme.colorScheme
    val (icon, tint, label) = when (found) {
        true -> Triple(Icons.Filled.CheckCircle, cs.secondary, "Binary found")
        false -> Triple(Icons.Filled.ErrorOutline, cs.error, "Binary not found")
        null -> Triple(Icons.Filled.ErrorOutline, cs.onSurfaceVariant, "Not validated")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BinaryActions(
    isDownloadSupported: Boolean,
    isDownloading: Boolean,
    onBrowse: () -> Unit,
    onValidate: () -> Unit,
    onDownload: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Browse (file picker)
        OutlinedButton(
            onClick = onBrowse,
            enabled = !isDownloading,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.onSurface),
        ) {
            Icon(Icons.Filled.FolderOpen, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Browse", style = MaterialTheme.typography.labelMedium)
        }

        // Validate
        OutlinedButton(
            onClick = onValidate,
            enabled = !isDownloading,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.onSurface),
        ) {
            Icon(Icons.Filled.Check, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Validate", style = MaterialTheme.typography.labelMedium)
        }

        // Download (only on supported platforms)
        if (isDownloadSupported) {
            Button(
                onClick = onDownload,
                enabled = !isDownloading,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.secondary,
                    contentColor = cs.onSecondary,
                ),
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = cs.onSecondary,
                    )
                } else {
                    Icon(Icons.Filled.CloudDownload, null, Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isDownloading) "Downloading…" else "Download",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun DownloadProgress(progress: Float) {
    val cs = MaterialTheme.colorScheme
    Column {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = cs.secondary,
            trackColor = cs.surfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Field helpers
// ---------------------------------------------------------------------------

@Composable
private fun ConfigTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.secondary,
            focusedLabelColor = MaterialTheme.colorScheme.secondary,
            cursorColor = MaterialTheme.colorScheme.secondary,
        ),
    )
}

@Composable
private fun ConfigSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.secondary,
                checkedTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
            ),
            modifier = Modifier.semantics {
                stateDescription = if (checked) "enabled" else "disabled"
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Theme picker — 6 color swatches
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemePicker(selected: AppThemeMode, onSelect: (AppThemeMode) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppThemeMode.entries.forEach { mode ->
            ThemeSwatch(
                mode = mode,
                isSelected = mode == selected,
                onClick = { onSelect(mode) },
            )
        }
    }
}

@Composable
private fun ThemeSwatch(
    mode: AppThemeMode,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = colorSchemeFor(mode)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(scheme.primary)
            .then(
                if (isSelected) Modifier.border(3.dp, scheme.secondary, CircleShape)
                else Modifier.border(1.dp, scheme.outline, CircleShape)
            )
            .clickable(onClick = onClick)
            .semantics { role = Role.RadioButton },
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected: ${mode.name}",
                tint = scheme.secondary,
                modifier = Modifier.size(20.dp),
            )
        }
        // Show accent dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(scheme.secondary)
                .align(Alignment.BottomEnd)
                .padding(2.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Language picker — 12 locales dropdown
// ---------------------------------------------------------------------------

private val SUPPORTED_LOCALES = listOf(
    "en" to "English",
    "zh" to "中文",
    "es" to "Español",
    "fr" to "Français",
    "de" to "Deutsch",
    "ru" to "Русский",
    "pt" to "Português",
    "ja" to "日本語",
    "ko" to "한국어",
    "id" to "Bahasa Indonesia",
    "ar" to "العربية",
    "hi" to "हिन्दी",
)

@Composable
private fun LanguagePicker(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = SUPPORTED_LOCALES.firstOrNull { it.first == selected }?.second ?: selected

    Box {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Language") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                focusedLabelColor = MaterialTheme.colorScheme.secondary,
            ),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SUPPORTED_LOCALES.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    },
                    trailingIcon = if (code == selected) ({
                        Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.secondary)
                    }) else null,
                )
            }
        }
    }
}
