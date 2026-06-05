package com.mozeeb.picoclaw.cmp

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.mozeeb.picoclaw.cmp.core.AppSettings
import com.mozeeb.picoclaw.cmp.core.WindowStateStore
import com.mozeeb.picoclaw.cmp.di.initKoin
import com.mozeeb.picoclaw.cmp.mvi.ServiceIntent
import com.mozeeb.picoclaw.cmp.mvi.ServiceStatus
import com.mozeeb.picoclaw.cmp.mvi.ServiceViewModel
import com.mozeeb.picoclaw.cmp.ui.App
import kotlinx.coroutines.runBlocking

fun main() {
    // Initialize Koin DI (DataStore + CoreServiceAdapter + BinaryDownloader + ViewModel)
    val koin = initKoin()

    // Restore the last window size/position before opening the window
    val windowStore = WindowStateStore(koin.get<AppSettings>())
    val savedBounds = runBlocking { windowStore.load() }

    application {
        // One ServiceViewModel shared between the system tray and the window UI,
        // so tray-initiated start/stop is reflected in the UI and vice-versa.
        val viewModel = remember { ServiceViewModel(koin.get(), koin.get(), koin.get(), koin.get()) }
        val state by viewModel.state.collectAsState()

        var windowVisible by remember { mutableStateOf(true) }

        val windowState = rememberWindowState(
            size = DpSize(savedBounds.width.dp, savedBounds.height.dp),
            position = if (savedBounds.hasPosition) {
                WindowPosition(savedBounds.x.dp, savedBounds.y.dp)
            } else {
                WindowPosition(Alignment.Center)
            },
        )

        // Persist window bounds whenever they change
        LaunchedEffect(windowState) {
            snapshotFlow { windowState.size to windowState.position }
                .collect { (size, pos) ->
                    val abs = pos as? WindowPosition.Absolute
                    windowStore.save(
                        width = size.width.value.toInt(),
                        height = size.height.value.toInt(),
                        x = abs?.x?.value?.toInt() ?: WindowStateStore.UNSET,
                        y = abs?.y?.value?.toInt() ?: WindowStateStore.UNSET,
                    )
                }
        }

        // ── System tray ──────────────────────────────────────────────────────
        val trayState = rememberTrayState()
        Tray(
            state = trayState,
            // Brand-cyan square icon (replaceable with a bundled icon resource later)
            icon = ColorPainter(Color(0xFF00E5FF)),
            tooltip = "PicoClaw — ${state.status.name}",
            onAction = { windowVisible = true }, // left-click tray → show window
            menu = {
                Item(if (windowVisible) "Hide window" else "Show window") {
                    windowVisible = !windowVisible
                }
                Separator()
                Item(
                    "Start service",
                    enabled = state.status == ServiceStatus.Stopped,
                ) { viewModel.onIntent(ServiceIntent.StartService) }
                Item(
                    "Stop service",
                    enabled = state.status == ServiceStatus.Running,
                ) { viewModel.onIntent(ServiceIntent.StopService) }
                Separator()
                Item("Quit PicoClaw") {
                    viewModel.onIntent(ServiceIntent.StopService)
                    exitApplication()
                }
            },
        )

        // ── Main window (minimize-to-tray on close) ──────────────────────────
        Window(
            onCloseRequest = { windowVisible = false },
            visible = windowVisible,
            state = windowState,
            title = "PicoClaw",
        ) {
            App(viewModel)
        }
    }
}
