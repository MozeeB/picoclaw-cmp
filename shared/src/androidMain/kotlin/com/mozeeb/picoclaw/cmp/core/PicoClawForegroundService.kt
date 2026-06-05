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
        val host       = intent.getStringExtra(EXTRA_HOST)   ?: "127.0.0.1"
        val port       = intent.getIntExtra(EXTRA_PORT, 18800)
        val path       = intent.getStringExtra(EXTRA_PATH)   ?: "/"
        val binaryPath = intent.getStringExtra(EXTRA_BINARY) ?: ""
        val extraArgs  = intent.getStringExtra(EXTRA_ARGS)   ?: ""

        processJob?.cancel()
        processJob = scope.launch {
            runCatching {
                val binary = File(binaryPath)
                if (!binary.exists() || !binary.canExecute()) {
                    updateNotification("Error: binary not found at $binaryPath")
                    Log.e(TAG, "Binary not found or not executable: $binaryPath")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                // Build command — mirrors Flutter's PicoClawService.runWebService()
                val cmd = buildList {
                    add(binaryPath)
                    add("--host"); add(host)
                    add("--port"); add(port.toString())
                    if (path.isNotBlank() && path != "/") { add("--path"); add(path) }
                    if (extraArgs.isNotBlank()) addAll(extraArgs.trim().split("\\s+".toRegex()))
                }

                Log.i(TAG, "Starting: ${cmd.joinToString(" ")}")
                updateNotification("PicoClaw — running on $host:$port")

                val pb = ProcessBuilder(cmd)
                    .directory(filesDir)
                    .redirectErrorStream(true)

                // Set environment variables (mirrors Flutter's buildEnvironment())
                pb.environment().also { env ->
                    val picoHome = File(filesDir, "picoclaw").also { it.mkdirs() }
                    val tmpDir   = File(cacheDir, "tmp").also { it.mkdirs() }
                    env["HOME"]             = filesDir.absolutePath
                    env["PICOCLAW_HOME"]    = picoHome.absolutePath
                    env["PICOCLAW_CONFIG"]  = File(picoHome, "config.json").absolutePath
                    env["PICOCLAW_BINARY"]  = binaryPath
                    env["TMPDIR"]           = tmpDir.absolutePath
                    env["PATH"]             = "/system/bin:/system/xbin"
                    env["LANG"]             = "en_US.UTF-8"
                    env["SSL_CERT_DIR"]     = "/system/etc/security/cacerts"
                }

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
