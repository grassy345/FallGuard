package com.fallguard.app

import android.Manifest
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
 * What it does (Feature 1):
 * 1. Starts the background monitoring service when the app opens
 * 2. Asks for notification permission (Android 13+ requires this)
 * 3. Asks Android not to kill our app to save battery
 * 4. Shows a simple "Monitoring Active" status text
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

        // Step 2: Ask Android not to kill our app to save battery
        requestBatteryOptimizationExemption()

        // Step 3: Start the background monitoring service
        startFallDetectionService()

        // Step 4: Update the status text on screen
        val statusText = findViewById<TextView>(R.id.statusText)
        statusText.text = "✅ Monitoring Active\n\nFallGuard is listening for alerts.\nYou can close this app — monitoring continues in the background."
    }

    /**
     * Starts the FallDetectionService as a foreground service.
     * A foreground service stays running even when you close the app.
     */
    private fun startFallDetectionService() {
        val serviceIntent = Intent(this, FallDetectionService::class.java)
        // startForegroundService() is used for services that show a persistent notification
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    /**
     * On Android 13 (API 33) and above, apps must explicitly ask the user
     * for permission to show notifications. Without this, our alerts won't appear!
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
     * Asks Android: "Please don't kill our app to save battery."
     * This is important because our app needs to run 24/7 to catch fall alerts.
     * The user will see a system dialog asking them to allow this.
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
