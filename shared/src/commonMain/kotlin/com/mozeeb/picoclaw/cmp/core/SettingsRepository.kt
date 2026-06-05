package com.mozeeb.picoclaw.cmp.core

/**
 * Persists user preferences and configuration via [AppSettings].
 * Implementations are platform-specific (DataStore on Android/JVM/iOS, in-memory on JS/WasmJS).
 */
open class SettingsRepository(private val settings: AppSettings) {

    companion object {
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_PATH = "path"
        const val KEY_BINARY_PATH = "binary_path"
        const val KEY_EXTRA_ARGS = "extra_args"
        const val KEY_AUTO_START = "auto_start"
        const val KEY_THEME = "theme"
        const val KEY_LOCALE = "locale"
        const val KEY_TELEMETRY = "telemetry_enabled"
        const val KEY_PUBLIC_MODE = "public_mode"
    }

    open suspend fun loadConfig(): AppConfig = AppConfig(
        host = settings.getString(KEY_HOST, "127.0.0.1"),
        port = settings.getInt(KEY_PORT, 18800),
        path = settings.getString(KEY_PATH, "/"),
        binaryPath = settings.getString(KEY_BINARY_PATH, ""),
        extraArgs = settings.getString(KEY_EXTRA_ARGS, ""),
        autoStart = settings.getBoolean(KEY_AUTO_START, false),
        themeName = settings.getString(KEY_THEME, "Carbon"),
        locale = settings.getString(KEY_LOCALE, "en"),
        telemetryEnabled = settings.getBoolean(KEY_TELEMETRY, false),
        publicMode = settings.getBoolean(KEY_PUBLIC_MODE, false),
    )

    open suspend fun saveConfig(
        host: String,
        port: Int,
        path: String,
        binaryPath: String,
        extraArgs: String,
        autoStart: Boolean,
    ) {
        settings.putString(KEY_HOST, host)
        settings.putInt(KEY_PORT, port)
        settings.putString(KEY_PATH, path)
        settings.putString(KEY_BINARY_PATH, binaryPath)
        settings.putString(KEY_EXTRA_ARGS, extraArgs)
        settings.putBoolean(KEY_AUTO_START, autoStart)
    }

    open suspend fun saveTheme(themeName: String) = settings.putString(KEY_THEME, themeName)
    open suspend fun saveLocale(locale: String) = settings.putString(KEY_LOCALE, locale)
    open suspend fun saveTelemetry(enabled: Boolean) = settings.putBoolean(KEY_TELEMETRY, enabled)
    open suspend fun savePublicMode(enabled: Boolean) = settings.putBoolean(KEY_PUBLIC_MODE, enabled)
}

data class AppConfig(
    val host: String,
    val port: Int,
    val path: String,
    val binaryPath: String,
    val extraArgs: String,
    val autoStart: Boolean,
    val themeName: String,
    val locale: String,
    val telemetryEnabled: Boolean,
    val publicMode: Boolean = false,
)
