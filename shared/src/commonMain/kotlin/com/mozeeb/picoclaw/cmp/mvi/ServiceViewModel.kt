package com.mozeeb.picoclaw.cmp.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mozeeb.picoclaw.cmp.core.Analytics
import com.mozeeb.picoclaw.cmp.core.BinaryDownloader
import com.mozeeb.picoclaw.cmp.core.BinaryNotFoundException
import com.mozeeb.picoclaw.cmp.core.BinaryValidation
import com.mozeeb.picoclaw.cmp.core.CoreServiceAdapter
import com.mozeeb.picoclaw.cmp.core.DownloadResult
import com.mozeeb.picoclaw.cmp.core.SettingsRepository
import com.mozeeb.picoclaw.cmp.core.pickBinaryFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel. Processes [ServiceIntent]s and emits a new [ServiceState] for every change.
 * The state is immutable — every update produces a new copy via [ServiceState.copy].
 */
class ServiceViewModel(
    private val adapter: CoreServiceAdapter,
    private val settings: SettingsRepository,
    private val downloader: BinaryDownloader,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ServiceState(isDownloadSupported = downloader.isSupported),
    )
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            loadSettings()
            // Apply the persisted telemetry preference to analytics on launch.
            analytics.setEnabled(_state.value.isTelemetryEnabled)
            // Auto-validate binary after settings are loaded so the UI can show
            // the "binary not found" banner immediately on launch.
            validateBinary()
        }
        viewModelScope.launch { collectAdapterLogs() }
    }

    // -------------------------------------------------------------------------
    // Public API — single entry point for all state changes
    // -------------------------------------------------------------------------

    fun onIntent(intent: ServiceIntent) {
        when (intent) {
            ServiceIntent.StartService   -> startService()
            ServiceIntent.StopService    -> stopService()
            ServiceIntent.ValidateBinary -> viewModelScope.launch { validateBinary() }
            ServiceIntent.PickBinaryFile -> pickBinary()
            ServiceIntent.DownloadBinary -> downloadBinary()

            is ServiceIntent.UpdateHost        -> _state.update { it.copy(host = intent.host, configDirty = true) }
            is ServiceIntent.UpdatePort        -> _state.update { it.copy(port = intent.port, configDirty = true) }
            is ServiceIntent.UpdatePath        -> _state.update { it.copy(path = intent.path, configDirty = true) }
            is ServiceIntent.UpdateBinaryPath  -> {
                _state.update { it.copy(binaryPath = intent.path, configDirty = true, binaryFound = null) }
                // Re-validate whenever the binary path changes
                viewModelScope.launch { validateBinary() }
            }
            is ServiceIntent.UpdateExtraArgs   -> _state.update { it.copy(extraArgs = intent.args, configDirty = true) }
            is ServiceIntent.ToggleAutoStart   -> _state.update { it.copy(autoStart = intent.enabled, configDirty = true) }

            ServiceIntent.SaveConfig    -> saveConfig()
            ServiceIntent.DiscardConfig -> viewModelScope.launch { loadSettings() }

            is ServiceIntent.SelectTheme  -> selectTheme(intent.theme)
            is ServiceIntent.SelectLocale -> selectLocale(intent.locale)

            is ServiceIntent.TogglePublicMode -> togglePublicMode(intent.enabled)
            is ServiceIntent.SetDeviceIp      -> _state.update { it.copy(deviceIp = intent.ip) }

            is ServiceIntent.ToggleTelemetry -> toggleTelemetry(intent.enabled)

            is ServiceIntent.AppendLog -> _state.update { it.copy(logs = it.logs + intent.line) }
            ServiceIntent.ClearLogs    -> _state.update { it.copy(logs = emptyList()) }
            ServiceIntent.ExportLogs   -> exportLogs()

            ServiceIntent.DismissError -> _state.update { it.copy(errorMessage = null) }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun startService() {
        if (_state.value.status != ServiceStatus.Stopped) return
        viewModelScope.launch { doStart() }
    }

    /** Actually launch the service from the current state. Reused by start + public-mode restart. */
    private suspend fun doStart() {
        val current = _state.value
        _state.update { it.copy(status = ServiceStatus.Starting, errorMessage = null) }
        try {
            // Public mode: the -public flag makes the binary listen on all interfaces (LAN).
            // Mirrors picoclaw_fui ServiceManager.start().
            val effectiveArgs = buildEffectiveArgs(current.extraArgs, current.publicMode)
            adapter.start(
                host = current.bindHost,
                port = current.port,
                path = current.path,
                binaryPath = current.binaryPath,
                extraArgs = effectiveArgs,
            )
            _state.update { it.copy(status = ServiceStatus.Running, binaryFound = true) }
            analytics.logEvent(Analytics.EVENT_SERVICE_START, mapOf("public" to current.publicMode.toString()))
        } catch (e: BinaryNotFoundException) {
            _state.update {
                it.copy(
                    status = ServiceStatus.Stopped,
                    binaryFound = false,
                    binarySearchedPaths = e.searchedPaths,
                    errorMessage = "Binary not found. Set the path in Config → Binary path.",
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    status = ServiceStatus.Stopped,
                    errorMessage = e.message ?: "Unknown error starting service",
                )
            }
        }
    }

    private fun stopService() {
        if (_state.value.status == ServiceStatus.Stopped) return
        _state.update { it.copy(status = ServiceStatus.Stopping) }
        viewModelScope.launch {
            try {
                adapter.stop()
            } finally {
                _state.update { it.copy(status = ServiceStatus.Stopped) }
                analytics.logEvent(Analytics.EVENT_SERVICE_STOP)
            }
        }
    }

    /** Persist the telemetry preference and propagate it to analytics collection. */
    private fun toggleTelemetry(enabled: Boolean) {
        _state.update { it.copy(isTelemetryEnabled = enabled) }
        analytics.setEnabled(enabled)
        viewModelScope.launch { settings.saveTelemetry(enabled) }
    }

    private suspend fun validateBinary() {
        val binaryPath = _state.value.binaryPath
        when (val result = adapter.validateBinary(binaryPath)) {
            is BinaryValidation.Ok -> _state.update {
                it.copy(binaryFound = true, binarySearchedPaths = emptyList())
            }
            is BinaryValidation.NotFound -> _state.update {
                it.copy(binaryFound = false, binarySearchedPaths = result.searchedPaths)
            }
        }
    }

    /**
     * Toggle public mode:
     * - persist the flag
     * - when enabled, fetch the device's LAN IP so the URL/QR code are scannable from another device
     * - when disabled, clear the device IP (URL/QR fall back to the configured host)
     */
    private fun togglePublicMode(enabled: Boolean) {
        val wasRunning = _state.value.status == ServiceStatus.Running
        _state.update { it.copy(publicMode = enabled) }
        viewModelScope.launch {
            settings.savePublicMode(enabled)
            _state.update { it.copy(deviceIp = if (enabled) adapter.getDeviceIpAddress() else null) }

            // The bind mode (`-public`) is fixed when the process starts, so a running service
            // must be restarted for the change to take effect (else it stays localhost-only).
            if (wasRunning) {
                _state.update { it.copy(status = ServiceStatus.Stopping) }
                runCatching { adapter.stop() }
                _state.update { it.copy(status = ServiceStatus.Stopped) }
                delay(800) // let the port free up before re-binding
                doStart()
            }
        }
    }

    /** Append the `-public` flag to the user args when public mode is on (deduplicated). */
    private fun buildEffectiveArgs(extraArgs: String, publicMode: Boolean): String {
        val tokens = extraArgs.split(' ').filter { it.isNotBlank() }.toMutableList()
        if (publicMode && !tokens.contains("-public")) tokens.add("-public")
        return tokens.joinToString(" ")
    }

    /** Open the platform file picker and apply the chosen binary path. */
    private fun pickBinary() {
        viewModelScope.launch {
            val picked = pickBinaryFile() ?: return@launch
            _state.update { it.copy(binaryPath = picked, configDirty = true) }
            settings.saveConfig(
                host = _state.value.host,
                port = _state.value.port,
                path = _state.value.path,
                binaryPath = picked,
                extraArgs = _state.value.extraArgs,
                autoStart = _state.value.autoStart,
            )
            _state.update { it.copy(configDirty = false) }
            validateBinary()
        }
    }

    /** Download the latest binary from GitHub releases, then validate it. */
    private fun downloadBinary() {
        if (_state.value.isDownloading) return
        if (!downloader.isSupported) {
            _state.update { it.copy(errorMessage = "Binary download is not supported on this platform.") }
            return
        }
        _state.update { it.copy(isDownloading = true, downloadProgress = 0f, errorMessage = null) }
        viewModelScope.launch {
            val result = downloader.downloadLatest { progress ->
                _state.update { it.copy(downloadProgress = progress) }
            }
            when (result) {
                is DownloadResult.Success -> {
                    _state.update {
                        it.copy(
                            isDownloading = false,
                            downloadProgress = 1f,
                            binaryPath = result.installedPath,
                            binaryFound = true,
                            binarySearchedPaths = emptyList(),
                        )
                    }
                    analytics.logEvent(Analytics.EVENT_BINARY_DOWNLOAD, mapOf("result" to "success"))
                    // Persist the resolved binary path
                    settings.saveConfig(
                        host = _state.value.host,
                        port = _state.value.port,
                        path = _state.value.path,
                        binaryPath = result.installedPath,
                        extraArgs = _state.value.extraArgs,
                        autoStart = _state.value.autoStart,
                    )
                    validateBinary()
                }
                is DownloadResult.Failure -> _state.update {
                    it.copy(isDownloading = false, errorMessage = "Download failed: ${result.message}")
                }
                DownloadResult.Unsupported -> _state.update {
                    it.copy(isDownloading = false, errorMessage = "Binary download is not supported on this platform.")
                }
            }
        }
    }

    private fun saveConfig() {
        val s = _state.value
        viewModelScope.launch {
            settings.saveConfig(
                host = s.host,
                port = s.port,
                path = s.path,
                binaryPath = s.binaryPath,
                extraArgs = s.extraArgs,
                autoStart = s.autoStart,
            )
            _state.update { it.copy(configDirty = false) }
        }
    }

    private fun selectTheme(theme: com.mozeeb.picoclaw.cmp.ui.AppThemeMode) {
        _state.update { it.copy(theme = theme) }
        analytics.logEvent(Analytics.EVENT_THEME_SELECT, mapOf("theme" to theme.name))
        viewModelScope.launch { settings.saveTheme(theme.name) }
    }

    private fun selectLocale(locale: String) {
        _state.update { it.copy(locale = locale) }
        viewModelScope.launch { settings.saveLocale(locale) }
    }

    private fun exportLogs() {
        viewModelScope.launch {
            try {
                adapter.exportLogs(_state.value.logs)
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Export failed: ${e.message}") }
            }
        }
    }

    private suspend fun loadSettings() {
        val config = settings.loadConfig()
        _state.update {
            it.copy(
                host = config.host,
                port = config.port,
                path = config.path,
                binaryPath = config.binaryPath,
                extraArgs = config.extraArgs,
                autoStart = config.autoStart,
                theme = com.mozeeb.picoclaw.cmp.ui.AppThemeMode.entries
                    .firstOrNull { m -> m.name == config.themeName }
                    ?: com.mozeeb.picoclaw.cmp.ui.AppThemeMode.Carbon,
                locale = config.locale,
                isTelemetryEnabled = config.telemetryEnabled,
                publicMode = config.publicMode,
                configDirty = false,
            )
        }
        // If public mode was persisted as on, fetch the device IP so the URL/QR are
        // immediately scannable on launch.
        if (config.publicMode) {
            val ip = adapter.getDeviceIpAddress()
            _state.update { it.copy(deviceIp = ip) }
        }
    }

    private suspend fun collectAdapterLogs() {
        adapter.logFlow.collect { line -> onIntent(ServiceIntent.AppendLog(line)) }
    }
}
