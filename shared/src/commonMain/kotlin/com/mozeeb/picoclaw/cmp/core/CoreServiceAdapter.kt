package com.mozeeb.picoclaw.cmp.core

import kotlinx.coroutines.flow.Flow

/**
 * Platform-specific adapter for starting/stopping the PicoClaw binary.
 *
 * All platforms MUST provide an actual implementation (or a no-op stub with a warning).
 *
 * Mirrors the interface from picoclaw_fui/lib/src/native/core_service_adapter.dart.
 */
interface CoreServiceAdapter {

    /** Emits log lines as the process produces them. */
    val logFlow: Flow<String>

    /**
     * Validate that the binary (optionally at [customPath]) is present and executable.
     *
     * Returns [BinaryValidation.Ok] when the binary can be run.
     * Returns [BinaryValidation.NotFound] with a resolved search path when missing.
     *
     * Mirrors: `Future<bool> validateBinary([String? path])` in the Flutter version.
     */
    suspend fun validateBinary(customPath: String = ""): BinaryValidation

    /**
     * Start the PicoClaw service with the given configuration.
     * Implementations MUST call [validateBinary] first and throw [BinaryNotFoundException]
     * if the binary is not found — never let a raw [java.io.IOException] propagate.
     */
    suspend fun start(
        host: String,
        port: Int,
        path: String,
        binaryPath: String,
        extraArgs: String,
    )

    /** Stop the running service. No-op if already stopped. */
    suspend fun stop()

    /** Export [logs] to a platform-appropriate destination (file, share sheet, etc.). */
    suspend fun exportLogs(logs: List<String>)

    /** Returns the device's local IPv4 address (for public mode). Null if unavailable. */
    suspend fun getDeviceIpAddress(): String?
}

// ---------------------------------------------------------------------------
// Binary validation result (sealed — exhaustive when)
// ---------------------------------------------------------------------------

sealed interface BinaryValidation {
    /** Binary found and executable. [resolvedPath] is the absolute path used. */
    data class Ok(val resolvedPath: String) : BinaryValidation

    /**
     * Binary not found.
     * [searchedPaths] lists every location that was checked, for display in the UI.
     */
    data class NotFound(val searchedPaths: List<String>) : BinaryValidation
}

// ---------------------------------------------------------------------------
// Exception thrown when start() is called and binary is missing
// ---------------------------------------------------------------------------

class BinaryNotFoundException(
    val searchedPaths: List<String>,
) : Exception(buildMessage(searchedPaths)) {

    companion object {
        private fun buildMessage(paths: List<String>): String = buildString {
            appendLine("PicoClaw binary not found.")
            appendLine("Searched locations:")
            paths.forEach { appendLine("  • $it") }
            append("Set the binary path in Config → Binary path, or download the binary.")
        }
    }
}
