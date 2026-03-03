package com.fallguard.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * FallDetectionService — The Heart of FallGuard
 *
 * This is a "foreground service" that runs silently in the background 24/7.
 *
 * What it does:
 * 1. Shows a persistent notification saying "FallGuard is monitoring..."
 * 2. Connects to Firebase Realtime Database
 * 3. Listens for changes to the "fall_status" field
 * 4. When FALL_DETECTED or SUSPICIOUS: launches AlarmActivity (full-screen alarm)
 * 5. When NORMAL: cancels any active alert notifications
 */
class FallDetectionService : Service() {

    companion object {
        private const val TAG = "FallDetectionService"

        // Notification channels
        private const val MONITORING_CHANNEL_ID = "fall_guard_monitoring"
        private const val ALERT_CHANNEL_ID = "fall_guard_alerts"

        // Notification IDs
        private const val MONITORING_NOTIFICATION_ID = 1
        private const val ALERT_NOTIFICATION_ID = 2

        private const val FIREBASE_PATH = "fall_alert"
    }

    // WakeLock keeps the CPU awake so we don't miss alerts when phone is sleeping
    private var wakeLock: PowerManager.WakeLock? = null

    // Firebase listener reference — we keep this so we can remove it when service stops
    private var firebaseListener: ValueEventListener? = null

    // Track the last status to avoid re-triggering for the same event
    private var lastFallStatus: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FallDetectionService created")

        // Create both notification channels
        createMonitoringChannel()
        createAlertChannel()

        // Start as a foreground service with a persistent notification
        startForeground(MONITORING_NOTIFICATION_ID, createMonitoringNotification())

        // Acquire a wake lock to keep CPU awake
        acquireWakeLock()

        // Start listening to Firebase for fall alerts
        startFirebaseListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FallDetectionService started")
        return START_STICKY
    }

    /**
     * Creates the persistent monitoring notification channel.
     * Low importance = no sound, just shows in notification bar quietly.
     */
    private fun createMonitoringChannel() {
        val channel = NotificationChannel(
            MONITORING_CHANNEL_ID,
            "Fall Detection Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification showing FallGuard is actively monitoring"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Creates the HIGH IMPORTANCE alert notification channel.
     * This channel:
     * - Bypasses Do Not Disturb
     * - Shows as a heads-up notification
     * - Can show full-screen intents (our AlarmActivity)
     * - NO sound — AlarmActivity handles the alarm audio to avoid double alarm
     */
    private fun createAlertChannel() {
        val channel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "Fall Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Emergency fall detection alerts — these bypass Do Not Disturb"
            setBypassDnd(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
            setSound(null, null)  // Silent! AlarmActivity plays the alarm sound instead
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Creates the persistent "FallGuard is monitoring..." notification.
     */
    private fun createMonitoringNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MONITORING_CHANNEL_ID)
            .setContentTitle("FallGuard Active")
            .setContentText("Monitoring for fall alerts...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FallGuard::MonitoringWakeLock"
        ).apply {
            acquire()
        }
    }

    /**
     * THE MOST IMPORTANT FUNCTION — Listens to Firebase Realtime Database.
     * Now triggers actual alarms instead of just logging!
     */
    private fun startFirebaseListener() {
        val database = FirebaseDatabase.getInstance()
        val fallAlertRef = database.getReference(FIREBASE_PATH)

        firebaseListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val fallStatus = snapshot.child("fall_status").getValue(String::class.java)
                val timestamp = snapshot.child("timestamp").getValue(String::class.java)
                val acknowledged = snapshot.child("acknowledged").getValue(Boolean::class.java)

                Log.d(TAG, "═══════════════════════════════════════════")
                Log.d(TAG, "Firebase data changed!")
                Log.d(TAG, "  fall_status: $fallStatus")
                Log.d(TAG, "  timestamp:   $timestamp")
                Log.d(TAG, "  acknowledged: $acknowledged")
                Log.d(TAG, "═══════════════════════════════════════════")

                // Don't trigger alarm if already acknowledged
                // BUT: reset lastFallStatus so the NEXT event (even same status) will trigger
                if (acknowledged == true) {
                    Log.d(TAG, "Alert already acknowledged — resetting lastFallStatus and skipping")
                    lastFallStatus = null  // Reset! So next FALL_DETECTED won't be blocked as duplicate
                    return
                }

                // Don't re-trigger for the same status
                if (fallStatus == lastFallStatus) {
                    Log.d(TAG, "Same status as before ($fallStatus) — skipping duplicate")
                    return
                }
                lastFallStatus = fallStatus

                // React based on fall status
                when (fallStatus) {
                    "FALL_DETECTED" -> {
                        Log.w(TAG, "🚨 FALL DETECTED! Launching AlarmActivity!")
                        launchAlarmActivity("FALL_DETECTED", timestamp ?: "")
                    }
                    "SUSPICIOUS" -> {
                        Log.w(TAG, "⚠️ SUSPICIOUS activity! Launching AlarmActivity!")
                        launchAlarmActivity("SUSPICIOUS", timestamp ?: "")
                    }
                    "NORMAL" -> {
                        Log.d(TAG, "✅ Status is NORMAL — cancelling any active alerts")
                        cancelAlertNotification()
                    }
                    else -> {
                        Log.d(TAG, "Unknown status: $fallStatus")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase listener cancelled: ${error.message}")
            }
        }

        fallAlertRef.addValueEventListener(firebaseListener!!)
        Log.d(TAG, "Firebase listener attached at path: $FIREBASE_PATH")
    }

    /**
     * Launches the full-screen AlarmActivity.
     * ALWAYS launches the activity directly (screen on or off).
     * Posts a quiet notification in the tray for reference only.
     */
    private fun launchAlarmActivity(alertType: String, timestamp: String) {
        // Step 1: Wake up the screen if it's off
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val screenWakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK
                or PowerManager.ACQUIRE_CAUSES_WAKEUP
                or PowerManager.ON_AFTER_RELEASE,
            "FallGuard::AlarmWakeLock"
        )
        screenWakeLock.acquire(30_000)  // Keep screen on for 30 seconds

        // Step 2: Create intent for AlarmActivity
        val alarmIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_ALERT_TYPE, alertType)
            putExtra(AlarmActivity.EXTRA_TIMESTAMP, timestamp)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }

        // Step 3: Launch the activity DIRECTLY — this is the ONLY way we show the alarm
        startActivity(alarmIntent)
        Log.d(TAG, "AlarmActivity launched directly for: $alertType")

        // Step 4: Post a QUIET notification in the tray (reference only, no sound, no heads-up)
        val tapIntent = PendingIntent.getActivity(
            this, 0, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (alertType == "FALL_DETECTED") "🚨 FALL DETECTED!" else "⚠️ SUSPICIOUS ACTIVITY"
        val displayTime = formatTimestamp(timestamp)

        val notification = NotificationCompat.Builder(this, MONITORING_CHANNEL_ID)  // Low priority channel!
            .setContentTitle(title)
            .setContentText("Tap to view alert — $displayTime")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_LOW)  // No heads-up, no interference
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    /**
     * Reformats the Python backend timestamp for display.
     * Input:  "01-03-2026 15:01:37" → Output: "01/03/2026 03:01:37 PM"
     */
    private fun formatTimestamp(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale.US)
            val date = inputFormat.parse(timestamp)
            if (date != null) outputFormat.format(date).uppercase() else timestamp
        } catch (e: Exception) {
            timestamp  // Fallback to raw timestamp
        }
    }

    /**
     * Cancels any active alert notification (when status returns to NORMAL).
     */
    private fun cancelAlertNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(ALERT_NOTIFICATION_ID)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FallDetectionService destroyed")

        firebaseListener?.let {
            FirebaseDatabase.getInstance().getReference(FIREBASE_PATH).removeEventListener(it)
        }

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
