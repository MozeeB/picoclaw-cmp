package com.mozeeb.picoclaw.cmp.mvi

import androidx.compose.runtime.Immutable
import com.mozeeb.picoclaw.cmp.ui.AppThemeMode

/**
 * Immutable snapshot of the entire application state.
 * All changes flow through [ServiceIntent] → [ServiceViewModel] → new copy.
 */
@Immutable
data class ServiceState(
    val status: ServiceStatus = ServiceStatus.Stopped,
    // Config
    val host: String = "127.0.0.1",
    val port: Int = 18800,
    val path: String = "/",
    val binaryPath: String = "",
    val extraArgs: String = "",
    val autoStart: Boolean = false,
    // UI preferences
    val theme: AppThemeMode = AppThemeMode.Carbon,
    val locale: String = "en",
    // Runtime
    val publicMode: Boolean = false,
    val deviceIp: String? = null,
    val logs: List<String> = emptyList(),
    val isTelemetryEnabled: Boolean = false,
    // Error / validation state
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val configDirty: Boolean = false,
    /**
     * Null = not yet validated.
     * True  = binary found.
     * False = binary not found — show banner guiding user to Config.
     */
    val binaryFound: Boolean? = null,
    /** Paths searched during the last failed [validateBinary] call. */
    val binarySearchedPaths: List<String> = emptyList(),
    // Binary download state
    val isDownloading: Boolean = false,
    /** Download progress in [0.0, 1.0]. */
    val downloadProgress: Float = 0f,
    /** Whether this platform supports runtime binary download (Desktop + Android). */
    val isDownloadSupported: Boolean = false,
) {
    /**
     * Address the binary should bind to.
     * In public mode it binds to all interfaces (0.0.0.0) so other LAN devices can connect;
     * otherwise it binds to the user-configured host (typically 127.0.0.1).
     */
    val bindHost: String
        get() = if (publicMode) "0.0.0.0" else host

    /**
     * Host shown in the URL/QR code.
     * In public mode, the device's LAN IP (so another device can scan and reach it);
     * otherwise the user-configured host.
     */
    val displayHost: String
        get() = if (publicMode) (deviceIp ?: host) else host

    /** URL shown to the user / encoded in the QR code (LAN IP in public mode). */
    val webUrl: String
        get() = buildString {
            append("http://")
            append(displayHost)
            append(":$port")
            if (path.isNotEmpty() && path != "/") append(path)
        }

    /**
     * URL the *embedded* WebView loads. Always targets the loopback host on this device —
     * the service binds `0.0.0.0` in public mode, so `127.0.0.1` is always reachable locally,
     * and Android's network-security-config permits cleartext only to loopback.
     */
    val localWebUrl: String
        get() = buildString {
            append("http://")
            append(if (publicMode) "127.0.0.1" else host)
            append(":$port")
            if (path.isNotEmpty() && path != "/") append(path)
        }

    /** True when the binary is known to be missing (not just unvalidated). */
    val isBinaryMissing: Boolean get() = binaryFound == false
}

enum class ServiceStatus {
    Stopped,
    Starting,
    Running,
    Stopping,
}
