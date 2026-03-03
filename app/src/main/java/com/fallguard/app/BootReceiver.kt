package com.fallguard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth

/**
 * BootReceiver — Auto-starts FallGuard when the phone restarts.
 *
 * Only starts the service if a user is logged in!
 * If the user logged out before the phone restarted, the service
 * stays off until they log in again.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Only start the service if a user is logged in
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null && currentUser.isEmailVerified) {
                Log.d("BootReceiver", "Phone restarted — user is logged in, starting monitoring service")
                val serviceIntent = Intent(context, FallDetectionService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                Log.d("BootReceiver", "Phone restarted — no user logged in, skipping service start")
            }
        }
    }
}
