package com.mozeeb.picoclaw.cmp.ui.pages

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mozeeb.picoclaw.cmp.core.UiConstants
import com.mozeeb.picoclaw.cmp.mvi.ServiceIntent
import com.mozeeb.picoclaw.cmp.mvi.ServiceState

/**
 * Log viewer page — mirrors picoclaw_fui/lib/src/ui/log_page.dart.
 * Features: real-time log list, filter/search, auto-scroll, export, clear.
 */
@Composable
fun LogPage(
    state: ServiceState,
    onIntent: (ServiceIntent) -> Unit,
) {
    val s = com.mozeeb.picoclaw.cmp.i18n.LocalStrings.current
    var filterText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val filteredLogs by remember(state.logs, filterText) {
        derivedStateOf {
            if (filterText.isBlank()) state.logs
            else state.logs.filter { it.contains(filterText, ignoreCase = true) }
        }
    }

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(UiConstants.contentPadding),
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = s.logsTitle,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            // Export button
            IconButton(
                onClick = { onIntent(ServiceIntent.ExportLogs) },
                modifier = Modifier
                    .size(UiConstants.minTouchTarget)
                    .semantics { role = Role.Button },
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = s.exportLogs,
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.width(4.dp))
            // Clear button
            IconButton(
                onClick = { onIntent(ServiceIntent.ClearLogs) },
                modifier = Modifier
                    .size(UiConstants.minTouchTarget)
                    .semantics { role = Role.Button },
            ) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = s.clearLogs,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Filter bar
        FilterBar(
            text = filterText,
            placeholder = s.filterLogs,
            onTextChange = { filterText = it },
        )

        Spacer(Modifier.height(8.dp))

        // Log count
        Text(
            text = "${filteredLogs.size} ${s.entries}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        // Log list
        if (filteredLogs.isEmpty()) {
            EmptyLogs(
                message = if (filterText.isNotBlank()) s.noLogsFiltered else s.noLogs,
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Key by index — log lines are often duplicated, so content-based keys
                // (e.g. hashCode) would collide and crash LazyColumn.
                items(count = filteredLogs.size, key = { index -> index }) { index ->
                    LogEntry(line = filteredLogs[index])
                }
            }
        }
    }
}

@Composable
private fun FilterBar(text: String, placeholder: String, onTextChange: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiConstants.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.FilterList,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Default,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
private fun LogEntry(line: String) {
    Text(
        text = line,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        ),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
private fun EmptyLogs(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
