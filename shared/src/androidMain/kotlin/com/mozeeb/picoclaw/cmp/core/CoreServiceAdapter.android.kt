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
        /** Native library name as packaged in jniLibs/. */
        const val BINARY_NAME = "libpicoclaw.so"
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
                ?.filter { it.isUp && !it.isLoopback }
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull()
                ?.hostAddress
        }.getOrNull()
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Build the ordered list of paths to check (mirrors Flutter's resolution order). */
    private fun buildCandidateList(customPath: String): List<String> = buildList {
        // 1. User-configured path (highest priority)
        if (customPath.isNotBlank()) add(File(customPath).absolutePath)

        // 2. nativeLibraryDir — Android auto-extracts jniLibs/ here on install
        add(File(context.applicationInfo.nativeLibraryDir, BINARY_NAME).absolutePath)

        // 3. filesDir — where we manually extract from APK on TV/unusual devices
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
