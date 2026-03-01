package com.fallguard.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * MainActivity — The main screen of FallGuard.
 *
 * What it does:
 * 1. Starts the background monitoring service when the app opens
 * 2. Asks for notification permission (Android 13+)
 * 3. Asks for DND override permission (so alarm plays even in Do Not Disturb)
 * 4. Asks Android not to kill our app to save battery
 * 5. Shows a simple "Monitoring Active" status text
 *
 * In future features, this will become a full dashboard.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Step 1: Ask for notification permission (only needed on Android 13+)
        requestNotificationPermission()

        // Step 2: Ask for DND override permission (so alarm bypasses Do Not Disturb)
        requestDndPermission()

        // Step 3: Ask Android not to kill our app to save battery
        requestBatteryOptimizationExemption()

        // Step 4: Start the background monitoring service
        startFallDetectionService()

        // Step 5: Update the status text on screen
        val statusText = findViewById<TextView>(R.id.statusText)
        statusText.text = "✅ Monitoring Active\n\nFallGuard is listening for alerts.\nYou can close this app — monitoring continues in the background."
    }

    private fun startFallDetectionService() {
        val serviceIntent = Intent(this, FallDetectionService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    /**
     * On Android 13+ apps must explicitly ask for notification permission.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }

    /**
     * Asks the user to grant DND (Do Not Disturb) override permission.
     * Without this, the alarm might not play when DND is enabled.
     * Opens the Android settings page for notification policy access.
     */
    private fun requestDndPermission() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
        }
    }

    /**
     * Asks Android not to kill our app to save battery.
     * Critical for 24/7 monitoring.
     */
    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}
