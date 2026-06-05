package com.mozeeb.picoclaw.cmp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mozeeb.picoclaw.cmp.core.AndroidCoreServiceAdapter
import com.mozeeb.picoclaw.cmp.core.BinaryValidation
import com.mozeeb.picoclaw.cmp.core.CoreServiceAdapter
import com.mozeeb.picoclaw.cmp.core.PicoClawForegroundService
import com.mozeeb.picoclaw.cmp.core.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Auto-starts the PicoClaw service on device boot when the user enabled
 * "Auto-start on launch" in Config. Mirrors picoclaw_fui's BootReceiver.
 *
 * Reads the persisted config (autoStart, binary path, host/port, public mode) via the
 * Koin-provided [SettingsRepository] and starts [PicoClawForegroundService] only if
 * auto-start is enabled.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val koin = GlobalContext.getOrNull() ?: run {
            Log.w(TAG, "Koin not initialized — cannot auto-start on boot")
            return
        }
        val settings = koin.get<SettingsRepository>()
        val adapter = koin.get<CoreServiceAdapter>() as? AndroidCoreServiceAdapter

        // BroadcastReceiver work must finish quickly; use goAsync for the suspend reads.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = settings.loadConfig()
                if (!config.autoStart) {
                    Log.i(TAG, "Auto-start disabled — skipping")
                    return@launch
                }

                // Resolve the binary the same way the adapter does at runtime
                val resolved = (adapter?.validateBinary(config.binaryPath) as? BinaryValidation.Ok)
                    ?.resolvedPath ?: config.binaryPath

                val args = buildList {
                    addAll(config.extraArgs.split(' ').filter { it.isNotBlank() })
                    if (config.publicMode && !contains("-public")) add("-public")
                }.joinToString(" ")

                val serviceIntent = Intent(context, PicoClawForegroundService::class.java).apply {
                    action = PicoClawForegroundService.ACTION_START
                    putExtra(PicoClawForegroundService.EXTRA_HOST, if (config.publicMode) "0.0.0.0" else config.host)
                    putExtra(PicoClawForegroundService.EXTRA_PORT, config.port)
                    putExtra(PicoClawForegroundService.EXTRA_PATH, config.path)
                    putExtra(PicoClawForegroundService.EXTRA_BINARY, resolved)
                    putExtra(PicoClawForegroundService.EXTRA_ARGS, args)
                }
                context.startForegroundService(serviceIntent)
                Log.i(TAG, "Auto-started PicoClaw service on boot")
            } catch (e: Exception) {
                Log.e(TAG, "Boot auto-start failed: ${e.message}", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "PicoClawBoot"
    }
}
