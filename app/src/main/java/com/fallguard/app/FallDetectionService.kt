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

/**
 * FallDetectionService — The Heart of FallGuard
 *
 * This is a "foreground service" that runs silently in the background 24/7.
 * 
 * What it does:
 * 1. Shows a persistent notification saying "FallGuard is monitoring..."
 *    (Android requires this for services that run in the background)
 * 2. Connects to Firebase Realtime Database
 * 3. Listens for changes to the "fall_status" field
 * 4. When a fall is detected, it logs the event (Feature 2 will add alarms)
 *
 * Why a FOREGROUND service?
 * - Regular background services get killed by Android to save battery
 * - Foreground services show a persistent notification and Android keeps them alive
 * - This is perfect for our use case: we need to ALWAYS be listening
 *
 * What is START_STICKY?
 * - If Android kills the service (low memory), START_STICKY tells it to restart it
 * - This means our monitoring comes back automatically
 */
class FallDetectionService : Service() {

    companion object {
        private const val TAG = "FallDetectionService"
        private const val CHANNEL_ID = "fall_guard_monitoring"
        private const val NOTIFICATION_ID = 1
        private const val FIREBASE_PATH = "fall_alert"
    }

    // WakeLock keeps the CPU awake so we don't miss alerts when phone is sleeping
    private var wakeLock: PowerManager.WakeLock? = null

    // Firebase listener reference — we keep this so we can remove it when service stops
    private var firebaseListener: ValueEventListener? = null

    /**
     * Called when the service is first created.
     * We set up the notification channel and start listening to Firebase here.
     */
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FallDetectionService created")

        // Create the notification channel (required on Android 8.0+)
        createNotificationChannel()

        // Start as a foreground service with a persistent notification
        startForeground(NOTIFICATION_ID, createMonitoringNotification())

        // Acquire a wake lock to keep CPU awake
        acquireWakeLock()

        // Start listening to Firebase for fall alerts
        startFirebaseListener()
    }

    /**
     * Called when the service is started (or restarted by the system).
     * START_STICKY means: "If I get killed, restart me!"
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FallDetectionService started")
        return START_STICKY
    }

    /**
     * Creates a notification channel.
     *
     * Think of a notification channel like a "category" for notifications.
     * Android lets users control each channel separately.
     * This channel is for the persistent "monitoring" notification.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Fall Detection Monitoring",
            NotificationManager.IMPORTANCE_LOW  // Low importance = no sound, just shows in notification bar
        ).apply {
            description = "Persistent notification showing FallGuard is actively monitoring"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Creates the persistent "FallGuard is monitoring..." notification.
     * This notification stays visible as long as the service is running.
     */
    private fun createMonitoringNotification(): Notification {
        // When the user taps the notification, open the main screen
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FallGuard Active")
            .setContentText("Monitoring for fall alerts...")
            .setSmallIcon(android.R.drawable.ic_menu_view)  // Temporary icon, will replace later
            .setContentIntent(pendingIntent)
            .setOngoing(true)  // Can't be swiped away
            .build()
    }

    /**
     * Keeps the CPU awake so we can receive Firebase updates even when the phone screen is off.
     */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FallGuard::MonitoringWakeLock"
        ).apply {
            acquire()  // Keep CPU awake indefinitely (released when service stops)
        }
    }

    /**
     * THE MOST IMPORTANT FUNCTION — Listens to Firebase Realtime Database.
     *
     * This connects to Firebase at the path "fall_alert" and listens for ANY changes.
     * When the Python backend writes something like:
     *   { "fall_status": "FALL_DETECTED", "timestamp": "...", "acknowledged": false }
     * ...this function gets called with the new data.
     *
     * For Feature 1, we just LOG the change.
     * Feature 2 will add push notifications and alarms.
     */
    private fun startFirebaseListener() {
        val database = FirebaseDatabase.getInstance()
        val fallAlertRef = database.getReference(FIREBASE_PATH)

        firebaseListener = object : ValueEventListener {
            /**
             * Called whenever data at "fall_alert" changes in Firebase.
             * This is triggered in real-time — the moment the Python backend writes data.
             */
            override fun onDataChange(snapshot: DataSnapshot) {
                // Read the values from Firebase
                val fallStatus = snapshot.child("fall_status").getValue(String::class.java)
                val timestamp = snapshot.child("timestamp").getValue(String::class.java)
                val acknowledged = snapshot.child("acknowledged").getValue(Boolean::class.java)

                Log.d(TAG, "═══════════════════════════════════════════")
                Log.d(TAG, "Firebase data changed!")
                Log.d(TAG, "  fall_status: $fallStatus")
                Log.d(TAG, "  timestamp:   $timestamp")
                Log.d(TAG, "  acknowledged: $acknowledged")
                Log.d(TAG, "═══════════════════════════════════════════")

                // React to fall detection
                when (fallStatus) {
                    "FALL_DETECTED" -> {
                        Log.w(TAG, "⚠️ FALL DETECTED! Alert should trigger here (Feature 2)")
                        // Feature 2 will add: trigger alarm, show notification
                    }
                    "SUSPICIOUS" -> {
                        Log.w(TAG, "⚠️ SUSPICIOUS activity detected! (Feature 2)")
                        // Feature 2 will add: trigger warning notification
                    }
                    else -> {
                        Log.d(TAG, "Status is normal or unknown: $fallStatus")
                    }
                }
            }

            /**
             * Called if Firebase can't be reached (no internet, etc.)
             */
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase listener cancelled: ${error.message}")
            }
        }

        // Attach the listener — Firebase will now notify us of ANY changes
        fallAlertRef.addValueEventListener(firebaseListener!!)
        Log.d(TAG, "Firebase listener attached at path: $FIREBASE_PATH")
    }

    /**
     * Called when the service is destroyed (stopped).
     * Clean up: remove Firebase listener, release wake lock.
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FallDetectionService destroyed")

        // Remove the Firebase listener
        firebaseListener?.let {
            FirebaseDatabase.getInstance().getReference(FIREBASE_PATH).removeEventListener(it)
        }

        // Release the wake lock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    /**
     * We don't support binding to this service (it's a started service, not bound).
     * This is required by Android but we just return null.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
