package com.mozeeb.picoclaw.cmp.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Android foreground Service that manages the picoclaw process lifecycle.
 *
 * The binary is always pre-validated by [AndroidCoreServiceAdapter] before
 * ACTION_START reaches here. We keep a second safety net — any error at this
 * level stops the notification gracefully rather than crashing the service.
 *
 * Environment variables mirror picoclaw_fui/android/PicoClawService.kt:
 *   HOME, PICOCLAW_HOME, PICOCLAW_CONFIG, PICOCLAW_BINARY, TMPDIR, PATH, LANG
 */
class PicoClawForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.mozeeb.picoclaw.cmp.ACTION_START"
        const val ACTION_STOP  = "com.mozeeb.picoclaw.cmp.ACTION_STOP"
        const val EXTRA_HOST   = "extra_host"
        const val EXTRA_PORT   = "extra_port"
        const val EXTRA_PATH   = "extra_path"
        const val EXTRA_BINARY = "extra_binary"
        const val EXTRA_ARGS   = "extra_args"
        private const val CHANNEL_ID      = "picoclaw_service"
        private const val NOTIFICATION_ID = 1001
        private const val TAG             = "PicoClawService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var process: Process? = null
    private var processJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("PicoClaw — starting…"))
                handleStart(intent)
            }
            ACTION_STOP -> handleStop()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    // -------------------------------------------------------------------------
    // Start — safe, mirrors Flutter's testBinary() → runService() pattern
    // -------------------------------------------------------------------------

    private fun handleStart(intent: Intent) {
        val port       = intent.getIntExtra(EXTRA_PORT, 18800)
        val binaryPath = intent.getStringExtra(EXTRA_BINARY) ?: ""
        val extraArgs  = intent.getStringExtra(EXTRA_ARGS)   ?: ""

        processJob?.cancel()
        processJob = scope.launch {
            runCatching {
                val webBinary = File(binaryPath)
                if (!webBinary.exists() || !webBinary.canExecute()) {
                    updateNotification("Error: binary not found at $binaryPath")
                    Log.e(TAG, "Binary not found or not executable: $binaryPath")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                val picoHome = File(filesDir, "picoclaw").also { it.mkdirs() }
                val configFile = File(picoHome, "config.json")
                // The web/launcher binary spawns the gateway, so PICOCLAW_BINARY must point at it.
                val gateway = resolveGatewayBinary()
                val env = buildEnv(picoHome, gateway?.absolutePath ?: binaryPath)

                // Onboard once (creates config.json) using the gateway — mirrors Flutter.
                if (!configFile.exists() && gateway != null) {
                    Log.i(TAG, "Onboarding (creating config.json)…")
                    updateNotification("PicoClaw — initializing…")
                    runCatching {
                        val ob = ProcessBuilder(gateway.absolutePath, "onboard")
                            .directory(filesDir).redirectErrorStream(true)
                        ob.environment().putAll(env)
                        val p = ob.start()
                        p.inputStream.bufferedReader().forEachLine { Log.d(TAG, it) }
                        p.waitFor()
                    }.onFailure { Log.w(TAG, "onboard failed: ${it.message}") }
                }

                // Build command. The web-console binary needs `--console --no-browser` + config path
                // (mirrors picoclaw_fui's PicoClawService); the launcher just needs `-port`.
                // Public mode is signalled via -public in extraArgs.
                val base = webBinary.name.lowercase()
                val isWebBinary = base.contains("web")
                val cmd = buildList {
                    add(binaryPath)
                    if (isWebBinary) { add("--console"); add("--no-browser") }
                    add("-port"); add(port.toString())
                    if (extraArgs.isNotBlank()) addAll(extraArgs.trim().split("\\s+".toRegex()))
                    if (isWebBinary && configFile.exists()) add(configFile.absolutePath)
                }

                Log.i(TAG, "Starting: ${cmd.joinToString(" ")}")
                updateNotification("PicoClaw — running on port $port")

                val pb = ProcessBuilder(cmd)
                    .directory(filesDir)
                    .redirectErrorStream(true)
                pb.environment().putAll(env)

                process = pb.start()

                // Stream stdout/stderr (daemon thread, mirrors Flutter's log reader)
                val logThread = Thread({
                    runCatching {
                        BufferedReader(InputStreamReader(process!!.inputStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                Log.d(TAG, line ?: continue)
                            }
                        }
                    }
                }, "picoclaw-log-reader").also { it.isDaemon = true; it.start() }

                val exitCode = process!!.waitFor()
                Log.i(TAG, "Process exited: code=$exitCode")
                updateNotification("PicoClaw — stopped (exit $exitCode)")
            }.onFailure { e ->
                Log.e(TAG, "Service error: ${e.message}", e)
                updateNotification("Error: ${e.message?.take(60)}")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    /** Resolve the gateway binary (libpicoclaw.so / downloaded picoclaw) for onboarding + env. */
    private fun resolveGatewayBinary(): File? {
        val nativeDir = applicationInfo.nativeLibraryDir
        return listOf(
            File(filesDir, "${AndroidCoreServiceAdapter.BIN_DIR}/picoclaw"),
            File(nativeDir, AndroidCoreServiceAdapter.BINARY_NAME),     // libpicoclaw.so
            File(filesDir, AndroidCoreServiceAdapter.BINARY_NAME),
        ).firstOrNull { it.exists() && it.canExecute() }
    }

    /** Environment for the picoclaw process (mirrors picoclaw_fui buildEnvironment). */
    private fun buildEnv(picoHome: File, gatewayPath: String): Map<String, String> {
        val tmpDir = File(cacheDir, "tmp").also { it.mkdirs() }
        return mapOf(
            "HOME" to filesDir.absolutePath,
            "PICOCLAW_HOME" to picoHome.absolutePath,
            "PICOCLAW_CONFIG" to File(picoHome, "config.json").absolutePath,
            "PICOCLAW_BINARY" to gatewayPath,
            "TMPDIR" to tmpDir.absolutePath,
            "PATH" to "/system/bin:/system/xbin",
            "LANG" to "en_US.UTF-8",
            "SSL_CERT_DIR" to "/system/etc/security/cacerts",
        )
    }

    // -------------------------------------------------------------------------
    // Stop
    // -------------------------------------------------------------------------

    private fun handleStop() {
        processJob?.cancel()
        runCatching { process?.let { if (it.isAlive) it.destroy() } }
        process = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { process?.destroy() }
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Notification helpers
    // -------------------------------------------------------------------------

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PicoClaw Service",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps the PicoClaw proxy running in the background"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val mainIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pi = mainIntent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        // Stop action inside the notification
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, PicoClawForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopAction = Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_pause),
            "Stop",
            stopPi,
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PicoClaw")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(pi)
            .addAction(stopAction)
            .build()
    }

    private fun updateNotification(text: String) {
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(text))
        }
    }
}
