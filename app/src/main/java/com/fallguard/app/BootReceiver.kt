package com.fallguard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * BootReceiver — Auto-starts FallGuard when the phone restarts.
 *
 * Without this, the caregiver would have to manually open the app
 * every time their phone restarts. That's dangerous — they might
 * forget, and miss a fall alert!
 *
 * How it works:
 * 1. Android sends a BOOT_COMPLETED broadcast when the phone finishes starting up
 * 2. This receiver catches that broadcast
 * 3. It starts the FallDetectionService automatically
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Phone restarted — starting FallGuard monitoring service")
            val serviceIntent = Intent(context, FallDetectionService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
