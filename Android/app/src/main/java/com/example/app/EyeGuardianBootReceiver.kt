package com.eyeguardian.app

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Attempts to restore protection after a device restart or app update.
 * Android may still require the user to reopen the app for camera foreground
 * service access, so the app explains that limitation in Settings.
 */
class EyeGuardianBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val store = EyeGuardianStore(context)
        if (!store.isMonitoringActive()) return

        runCatching {
            val serviceIntent = Intent(context, DistanceMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
