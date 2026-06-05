package com.mozeeb.picoclaw.cmp.mvi

import com.mozeeb.picoclaw.cmp.ui.AppThemeMode

/**
 * All user/system actions that can change [ServiceState].
 * No state is mutated directly — every change goes through an intent.
 */
sealed interface ServiceIntent {

    // --- Service lifecycle ---
    data object StartService : ServiceIntent
    data object StopService : ServiceIntent
    /** Run binary validation and update [ServiceState.binaryFound]. */
    data object ValidateBinary : ServiceIntent
    /** Open platform file-picker to let the user choose the binary. Result handled externally. */
    data object PickBinaryFile : ServiceIntent
    /** Download the latest core binary from GitHub releases (Desktop + Android only). */
    data object DownloadBinary : ServiceIntent

    // --- Config editing ---
    data class UpdateHost(val host: String) : ServiceIntent
    data class UpdatePort(val port: Int) : ServiceIntent
    data class UpdatePath(val path: String) : ServiceIntent
    data class UpdateBinaryPath(val path: String) : ServiceIntent
    data class UpdateExtraArgs(val args: String) : ServiceIntent
    data class ToggleAutoStart(val enabled: Boolean) : ServiceIntent
    data object SaveConfig : ServiceIntent
    data object DiscardConfig : ServiceIntent

    // --- UI preferences ---
    data class SelectTheme(val theme: AppThemeMode) : ServiceIntent
    data class SelectLocale(val locale: String) : ServiceIntent

    // --- Runtime toggles ---
    data class TogglePublicMode(val enabled: Boolean) : ServiceIntent
    data class SetDeviceIp(val ip: String?) : ServiceIntent

    // --- Telemetry ---
    data class ToggleTelemetry(val enabled: Boolean) : ServiceIntent

    // --- Logs ---
    data class AppendLog(val line: String) : ServiceIntent
    data object ClearLogs : ServiceIntent
    data object ExportLogs : ServiceIntent

    // --- Error handling ---
    data object DismissError : ServiceIntent
}
