package com.mozeeb.picoclaw.cmp.i18n

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Type-safe, in-house localization.
 *
 * All user-facing strings live here as a single immutable bundle. The active bundle is
 * provided at the App root via [LocalStrings], keyed on the selected locale, so switching
 * language recomposes the whole UI instantly — works identically on every platform without
 * relying on platform/system locale overrides.
 *
 * To add a language: add a bundle in `AppStringsLocales.kt` and a branch in [stringsFor].
 * English ([StringsEn]) is the canonical reference and the fallback for unknown locales.
 */
@Immutable
data class AppStrings(
    // Navigation
    val navDashboard: String,
    val navWeb: String,
    val navLogs: String,
    val navConfig: String,
    // Dashboard — status + controls
    val dashboardTitle: String,
    val statusRunning: String,
    val statusStarting: String,
    val statusStopping: String,
    val statusStopped: String,
    val start: String,
    val stop: String,
    val scanToOpen: String,
    val copyUrl: String,
    val urlCopied: String,
    // Dashboard — public mode
    val publicMode: String,
    val publicModeReachableAt: String,
    val publicModeNoIp: String,
    val publicModeHint: String,
    // Dashboard — binary banner
    val binaryNotFoundTitle: String,
    val binaryNotFoundDesc: String,
    val binaryNotFoundDescNoDownload: String,
    val downloadBinary: String,
    val downloading: String,
    val goToConfig: String,
    val searched: String,
    // WebView
    val serviceNotRunning: String,
    val webViewHint: String,
    val startService: String,
    // Logs
    val logsTitle: String,
    val filterLogs: String,
    val exportLogs: String,
    val clearLogs: String,
    val entries: String,
    val noLogs: String,
    val noLogsFiltered: String,
    // Config
    val configTitle: String,
    val sectionServer: String,
    val host: String,
    val port: String,
    val path: String,
    val sectionBinary: String,
    val binaryPath: String,
    val extraArgs: String,
    val autoStart: String,
    val browse: String,
    val validate: String,
    val download: String,
    val binaryFoundStatus: String,
    val binaryMissingStatus: String,
    val notValidated: String,
    val sectionTheme: String,
    val sectionLanguage: String,
    val sectionTelemetry: String,
    val enableAnalytics: String,
    val enableAnalyticsDesc: String,
    val saveConfig: String,
)

/** Canonical English bundle — also the fallback for unknown locales. */
val StringsEn: AppStrings = AppStrings(
    navDashboard = "Dashboard",
    navWeb = "Web",
    navLogs = "Logs",
    navConfig = "Config",
    dashboardTitle = "Dashboard",
    statusRunning = "Running",
    statusStarting = "Starting…",
    statusStopping = "Stopping…",
    statusStopped = "Stopped",
    start = "Start",
    stop = "Stop",
    scanToOpen = "Scan to open",
    copyUrl = "Copy URL",
    urlCopied = "URL copied to clipboard",
    publicMode = "Public Mode",
    publicModeReachableAt = "Reachable on your LAN at",
    publicModeNoIp = "Could not detect a LAN IP — check your network",
    publicModeHint = "Listen on all interfaces; use device LAN IP in QR/URL",
    binaryNotFoundTitle = "Binary not found",
    binaryNotFoundDesc = "The picoclaw binary was not found. Download it automatically, or set the path manually in Config.",
    binaryNotFoundDescNoDownload = "The picoclaw binary was not found in any of the default locations. Set the binary path in Config.",
    downloadBinary = "Download binary",
    downloading = "Downloading…",
    goToConfig = "Config →",
    searched = "Searched:",
    serviceNotRunning = "Service is not running",
    webViewHint = "Start the PicoClaw service to access the web UI.",
    startService = "Start Service",
    logsTitle = "Logs",
    filterLogs = "Filter logs…",
    exportLogs = "Export logs",
    clearLogs = "Clear logs",
    entries = "entries",
    noLogs = "No logs yet. Start the service to see output.",
    noLogsFiltered = "No logs matching filter.",
    configTitle = "Config",
    sectionServer = "Server",
    host = "Host",
    port = "Port",
    path = "Path",
    sectionBinary = "Binary",
    binaryPath = "Binary path",
    extraArgs = "Extra args",
    autoStart = "Auto-start on launch",
    browse = "Browse",
    validate = "Validate",
    download = "Download",
    binaryFoundStatus = "Binary found",
    binaryMissingStatus = "Binary not found",
    notValidated = "Not validated",
    sectionTheme = "Theme",
    sectionLanguage = "Language",
    sectionTelemetry = "Telemetry",
    enableAnalytics = "Enable analytics",
    enableAnalyticsDesc = "Help improve PicoClaw by sending anonymous usage data.",
    saveConfig = "Save config",
)

/** Returns the string bundle for [locale] (2-letter code), falling back to English. */
fun stringsFor(locale: String): AppStrings = when (locale.lowercase().take(2)) {
    "en" -> StringsEn
    "zh" -> StringsZh
    "es" -> StringsEs
    "fr" -> StringsFr
    "de" -> StringsDe
    "ru" -> StringsRu
    "pt" -> StringsPt
    "ja" -> StringsJa
    "ko" -> StringsKo
    "id" -> StringsId
    "ar" -> StringsAr
    "hi" -> StringsHi
    else -> StringsEn
}

/** Provides the active [AppStrings] down the composition. Defaults to English. */
val LocalStrings = staticCompositionLocalOf { StringsEn }
