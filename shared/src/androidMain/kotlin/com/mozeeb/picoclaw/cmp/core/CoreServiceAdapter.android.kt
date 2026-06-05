package com.mozeeb.picoclaw.cmp.core

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.zip.ZipFile

/**
 * Android CoreServiceAdapter.
 *
 * Binary resolution order (mirrors picoclaw_fui/android/PicoClawService.kt):
 *  1. User-provided [binaryPath] (from Config page)
 *  2. [nativeLibraryDir]/libpicoclaw.so  — Android auto-extracts from jniLibs/ on install
 *  3. [filesDir]/libpicoclaw.so          — manually extracted from APK (fallback for TV devices
 *                                          that don't auto-extract .so files from jniLibs/)
 *
 * Validates the binary before starting the foreground service so that
 * missing-binary errors are surfaced via the ViewModel (not as process crashes).
 */
class AndroidCoreServiceAdapter(private val context: Context) : CoreServiceAdapter {

    companion object {
        private const val TAG = "PicoClawAdapter"
        /** Bundled gateway, packaged in jniLibs/ (Cobra CLI — does NOT serve the web UI). */
        const val BINARY_NAME = "libpicoclaw.so"
        /** Bundled web-console binary, packaged in jniLibs/ (serves the management web UI). */
        const val WEB_BINARY_NAME = "libpicoclaw-web.so"
        /** Subdirectory of filesDir where downloaded executables are installed. */
        const val BIN_DIR = "picoclaw-bin"
    }

    private val _logFlow = MutableSharedFlow<String>(extraBufferCapacity = 1000)
    override val logFlow: Flow<String> = _logFlow

    // -------------------------------------------------------------------------
    // Binary validation — mirrors PicoClawService.resolveBinaryFile()
    // -------------------------------------------------------------------------

    override suspend fun validateBinary(customPath: String): BinaryValidation =
        withContext(Dispatchers.IO) {
            val candidates = buildCandidateList(customPath)

            // Check each candidate; attempt APK extraction if nativeLibraryDir misses
            for (path in candidates) {
                val file = File(path)
                if (file.canExecute()) {
                    Log.i(TAG, "Binary found: $path")
                    return@withContext BinaryValidation.Ok(path)
                }
            }

            // Attempt APK extraction as a last resort (TV / certain OEM devices)
            val extracted = extractBinaryFromApk()
            if (extracted != null && extracted.canExecute()) {
                Log.i(TAG, "Binary extracted from APK: ${extracted.absolutePath}")
                return@withContext BinaryValidation.Ok(extracted.absolutePath)
            }

            Log.w(TAG, "Binary not found in any location")
            BinaryValidation.NotFound(candidates)
        }

    // -------------------------------------------------------------------------
    // Start — validate first, then delegate to foreground service
    // -------------------------------------------------------------------------

    override suspend fun start(
        host: String,
        port: Int,
        path: String,
        binaryPath: String,
        extraArgs: String,
    ) = withContext(Dispatchers.IO) {
        val validation = validateBinary(binaryPath)
        if (validation is BinaryValidation.NotFound) {
            _logFlow.emit("[picoclaw] ERROR: Binary not found. Searched:")
            validation.searchedPaths.forEach { _logFlow.emit("  • $it") }
            _logFlow.emit("[picoclaw] Tip: Place libpicoclaw.so in your app's jniLibs/arm64-v8a/ folder, or set the binary path in Config.")
            throw BinaryNotFoundException(validation.searchedPaths)
        }
        val resolvedBinary = (validation as BinaryValidation.Ok).resolvedPath
        _logFlow.emit("[picoclaw] Binary resolved: $resolvedBinary")

        withContext(Dispatchers.Main) {
            val intent = Intent(context, PicoClawForegroundService::class.java).apply {
                action = PicoClawForegroundService.ACTION_START
                putExtra(PicoClawForegroundService.EXTRA_HOST, host)
                putExtra(PicoClawForegroundService.EXTRA_PORT, port)
                putExtra(PicoClawForegroundService.EXTRA_PATH, path)
                putExtra(PicoClawForegroundService.EXTRA_BINARY, resolvedBinary)
                putExtra(PicoClawForegroundService.EXTRA_ARGS, extraArgs)
            }
            context.startForegroundService(intent)
        }
        Unit
    }

    override suspend fun stop() = withContext(Dispatchers.Main) {
        _logFlow.emit("[picoclaw] Requesting service stop…")
        context.startService(
            Intent(context, PicoClawForegroundService::class.java).apply {
                action = PicoClawForegroundService.ACTION_STOP
            }
        )
        Unit
    }

    override suspend fun exportLogs(logs: List<String>) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = context.filesDir.resolve("picoclaw/logs").also { it.mkdirs() }
            dir.resolve("picoclaw_export.log").writeText(logs.joinToString("\n"))
            _logFlow.emit("[picoclaw] Logs exported to ${dir.absolutePath}/picoclaw_export.log")
        }.onFailure { _logFlow.emit("[picoclaw] Export failed: ${it.message}") }
        Unit
    }

    override suspend fun getDeviceIpAddress(): String? = withContext(Dispatchers.IO) {
        runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.asSequence()
                ?.filter { it.isUp && !it.isLoopback && !it.isVirtual }
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.filterIsInstance<Inet4Address>()
                // Skip loopback and link-local / APIPA (169.254.x.x) — not reachable on the LAN
                ?.filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                // Prefer real private LAN addresses (192.168/10/172.16-31)
                ?.sortedByDescending { it.isSiteLocalAddress }
                ?.firstOrNull()
                ?.hostAddress
        }.getOrNull()
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Build the ordered list of paths to check. The web-console binary (launcher / -web) is
     * preferred — the bare gateway can't serve the web UI. Gateway is only a last-resort fallback.
     */
    private fun buildCandidateList(customPath: String): List<String> = buildList {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val binDir = context.filesDir.resolve(BIN_DIR)

        // 1. User-configured path (highest priority)
        if (customPath.isNotBlank()) add(File(customPath).absolutePath)

        // 2. Web-console binaries (serve the UI) — downloaded (desktop + lib naming), then bundled
        add(File(binDir, "picoclaw-launcher").absolutePath)
        add(File(binDir, "picoclaw-web").absolutePath)
        add(File(binDir, WEB_BINARY_NAME).absolutePath)             // downloaded libpicoclaw-web.so
        add(File(nativeDir, WEB_BINARY_NAME).absolutePath)          // bundled libpicoclaw-web.so
        add(context.filesDir.resolve(WEB_BINARY_NAME).absolutePath)

        // 3. Gateway fallbacks (won't serve the web UI, but better than nothing)
        add(File(binDir, "picoclaw").absolutePath)
        add(File(binDir, BINARY_NAME).absolutePath)                 // downloaded libpicoclaw.so
        add(File(nativeDir, BINARY_NAME).absolutePath)              // bundled libpicoclaw.so
        add(context.filesDir.resolve(BINARY_NAME).absolutePath)
    }.distinct()

    /**
     * Mirrors Flutter's `extractBinaryFromApk()`.
     * Extracts [BINARY_NAME] from the APK's `lib/<abi>/` entry to [Context.getFilesDir].
     * Sets the file as executable after extraction.
     * Returns null if extraction fails (entry not found, I/O error, etc.).
     */
    private fun extractBinaryFromApk(): File? {
        return runCatching {
            val outputFile = context.filesDir.resolve(BINARY_NAME)

            // If already extracted and executable, reuse
            if (outputFile.exists() && outputFile.canExecute()) {
                Log.i(TAG, "Reusing cached extracted binary: ${outputFile.absolutePath}")
                return outputFile
            }

            // Determine the device ABI (prefer arm64-v8a, fall back to first supported ABI)
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            val apkPath = context.applicationInfo.sourceDir
            Log.i(TAG, "Extracting $BINARY_NAME from APK: $apkPath (abi=$abi)")

            ZipFile(apkPath).use { zip ->
                // Try device ABI first, then common fallbacks
                val entry = zip.getEntry("lib/$abi/$BINARY_NAME")
                    ?: zip.getEntry("lib/arm64-v8a/$BINARY_NAME")
                    ?: zip.getEntry("lib/armeabi-v7a/$BINARY_NAME")
                    ?: run {
                        Log.w(TAG, "$BINARY_NAME not found in APK")
                        return null
                    }

                FileOutputStream(outputFile).use { out ->
                    zip.getInputStream(entry).use { it.copyTo(out) }
                }
            }

            outputFile.setExecutable(true)
            Log.i(TAG, "Extracted $BINARY_NAME to ${outputFile.absolutePath}")
            outputFile
        }.onFailure {
            Log.e(TAG, "APK extraction failed: ${it.message}", it)
        }.getOrNull()
    }
}
