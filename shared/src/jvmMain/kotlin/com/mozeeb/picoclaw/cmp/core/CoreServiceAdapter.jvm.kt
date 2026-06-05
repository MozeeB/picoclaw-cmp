package com.mozeeb.picoclaw.cmp.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Desktop (JVM) CoreServiceAdapter.
 *
 * Binary resolution order (mirrors picoclaw_fui/desktop_core_service_adapter.dart):
 *  1. User-provided [binaryPath] (if set and file exists + executable)
 *  2. ~/.picoclaw/bin/picoclaw[.exe]
 *  3. ./bin/picoclaw[.exe]  (next to the running JAR / app directory)
 *  4. ./picoclaw[.exe]      (current working directory)
 *  5. System PATH entries
 *
 * [validateBinary] must be called (or [start] will call it) before spawning.
 * If the binary is not found a [BinaryNotFoundException] is thrown — never
 * a raw IOException.
 */
class DesktopCoreServiceAdapter : CoreServiceAdapter {

    private val _logFlow = MutableSharedFlow<String>(extraBufferCapacity = 1000)
    override val logFlow: Flow<String> = _logFlow

    private var process: Process? = null

    // -------------------------------------------------------------------------
    // Binary validation — public API
    // -------------------------------------------------------------------------

    override suspend fun validateBinary(customPath: String): BinaryValidation =
        withContext(Dispatchers.IO) {
            val candidates = resolveCandidates(customPath)
            val resolved = candidates.firstOrNull { File(it).canExecute() }
            if (resolved != null) BinaryValidation.Ok(resolved)
            else BinaryValidation.NotFound(candidates)
        }

    // -------------------------------------------------------------------------
    // Start / Stop
    // -------------------------------------------------------------------------

    override suspend fun start(
        host: String,
        port: Int,
        path: String,
        binaryPath: String,
        extraArgs: String,
    ) = withContext(Dispatchers.IO) {
        if (process?.isAlive == true) {
            _logFlow.emit("[picoclaw] Already running.")
            return@withContext
        }

        // Validate binary before spawning — throws BinaryNotFoundException if missing
        val validation = validateBinary(binaryPath)
        if (validation is BinaryValidation.NotFound) {
            _logFlow.emit("[picoclaw] ERROR: Binary not found. Searched:")
            validation.searchedPaths.forEach { _logFlow.emit("  • $it") }
            _logFlow.emit("[picoclaw] Tip: Set the binary path in Config → Binary path.")
            throw BinaryNotFoundException(validation.searchedPaths)
        }
        val resolvedBinary = (validation as BinaryValidation.Ok).resolvedPath

        // Invocation mirrors picoclaw_fui's desktop adapter: `<binary> -port <port> [args]`.
        // The binary serves the web console by default; public mode is signalled via the
        // `-public` flag (already folded into extraArgs by the ViewModel), NOT a --host flag.
        val cmd = buildList {
            add(resolvedBinary)
            add("-port"); add(port.toString())
            if (extraArgs.isNotBlank()) addAll(extraArgs.trim().split("\\s+".toRegex()))
        }

        _logFlow.emit("[picoclaw] Starting: ${cmd.joinToString(" ")}")

        val proc = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        process = proc

        // Stream stdout/stderr in a daemon thread
        Thread {
            proc.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { line -> _logFlow.tryEmit(line) }
            }
            _logFlow.tryEmit("[picoclaw] Process exited with code ${runCatching { proc.exitValue() }.getOrDefault(-1)}")
        }.also { it.isDaemon = true; it.name = "picoclaw-log-reader" }.start()
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        process?.let { proc ->
            if (proc.isAlive) {
                proc.destroy()
                _logFlow.emit("[picoclaw] Service stopped.")
            }
        }
        process = null
    }

    // -------------------------------------------------------------------------
    // Export / IP
    // -------------------------------------------------------------------------

    override suspend fun exportLogs(logs: List<String>) = withContext(Dispatchers.IO) {
        val homeDir = System.getProperty("user.home") ?: "."
        val logDir = File(homeDir, ".picoclaw/logs").also { it.mkdirs() }
        val logFile = File(logDir, "picoclaw_export.log")
        logFile.writeText(logs.joinToString("\n"))
        _logFlow.emit("[picoclaw] Logs exported to ${logFile.absolutePath}")
    }

    override suspend fun getDeviceIpAddress(): String? = withContext(Dispatchers.IO) {
        try {
            java.net.NetworkInterface.getNetworkInterfaces()
                ?.asSequence()
                ?.filter { it.isUp && !it.isLoopback && !it.isVirtual }
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.filterIsInstance<java.net.Inet4Address>()
                // Skip loopback (127.x) and link-local / APIPA (169.254.x.x) — not reachable on the LAN
                ?.filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                // Prefer real private LAN addresses (192.168/10/172.16-31) over anything else
                ?.sortedByDescending { it.isSiteLocalAddress }
                ?.firstOrNull()
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }

    // -------------------------------------------------------------------------
    // Binary resolution — mirrors Flutter's _resolveCoreExePath()
    // -------------------------------------------------------------------------

    private fun resolveCandidates(customPath: String): List<String> {
        val os = System.getProperty("os.name", "").lowercase()
        val isWindows = os.contains("win")
        val ext = if (isWindows) ".exe" else ""
        // Prefer the web-console launcher over the bare gateway CLI (which can't serve the web UI).
        val names = listOf("picoclaw-launcher$ext", "picoclaw$ext")
        val home = System.getProperty("user.home", ".")
        val cwd = System.getProperty("user.dir", ".")

        // Directories to search, in priority order
        val dirs = buildList {
            add(File(home, ".picoclaw/bin"))
            add(File(cwd, "bin"))
            add(File(cwd))
            runCatching {
                File(ProcessHandle.current().info().command().orElse("")).parentFile?.let {
                    add(it)
                    add(File(it, "bin"))
                }
            }
            (System.getenv("PATH") ?: "")
                .split(if (isWindows) ";" else ":")
                .filter { it.isNotBlank() }
                .forEach { add(File(it)) }
        }

        return buildList {
            // 1. User-configured path (exact) — highest priority
            if (customPath.isNotBlank()) add(File(customPath).absolutePath)
            // 2. launcher first, then gateway, across every search directory
            for (name in names) {
                for (dir in dirs) add(File(dir, name).absolutePath)
            }
        }.distinct()
    }
}
