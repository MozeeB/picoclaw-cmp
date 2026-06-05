package com.mozeeb.picoclaw.cmp.core

/**
 * Minimal analytics abstraction.
 *
 * The default implementation is a no-op ([NoOpAnalytics]); Android provides a stub
 * (`AndroidAnalytics`) that is ready to be backed by Firebase Analytics. All event
 * logging is gated by [setEnabled] so nothing is sent unless the user opts in
 * (see the Telemetry toggle in Config).
 */
interface Analytics {
    /** Enable or disable analytics collection (driven by the user's telemetry preference). */
    fun setEnabled(enabled: Boolean)

    /** Log a named event with optional string parameters. No-op when disabled. */
    fun logEvent(name: String, params: Map<String, String> = emptyMap())

    companion object {
        // Canonical event names
        const val EVENT_SERVICE_START = "service_start"
        const val EVENT_SERVICE_STOP = "service_stop"
        const val EVENT_THEME_SELECT = "theme_select"
        const val EVENT_BINARY_DOWNLOAD = "binary_download"
    }
}

/**
 * No-op analytics — used on Desktop, iOS, and Web.
 * Logs nothing externally; prints to stdout only when explicitly enabled (for local debugging).
 */
class NoOpAnalytics : Analytics {
    private var enabled = false
    override fun setEnabled(enabled: Boolean) { this.enabled = enabled }
    override fun logEvent(name: String, params: Map<String, String>) {
        if (enabled) println("[analytics] $name $params")
    }
}
