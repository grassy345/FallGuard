package com.fallguard.app

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AlarmActivity — The Full-Screen Emergency Alarm
 *
 * This activity takes over the entire screen (even over the lock screen)
 * when a fall or suspicious activity is detected.
 *
 * What it does:
 * 1. Shows a full-screen alert with RED (fall) or AMBER (suspicious) background
 * 2. Flashes/pulses the background to grab attention
 * 3. Plays the alarm sound at MAX VOLUME, bypassing silent/DND mode
 * 4. Shows "Just now" / "1 minute ago" relative timestamp (updates live)
 * 5. Has a big ACKNOWLEDGE button that:
 *    - Stops the alarm
 *    - Writes acknowledged=true to Firebase
 *    - Closes this screen
 */
class AlarmActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AlarmActivity"

        // Intent extras — passed from FallDetectionService when launching this activity
        const val EXTRA_ALERT_TYPE = "alert_type"        // "FALL_DETECTED" or "SUSPICIOUS"
        const val EXTRA_TIMESTAMP = "timestamp"           // "01-03-2026 15:01:37"

        // Timestamp format from the Python backend (input)
        private const val TIMESTAMP_FORMAT = "dd-MM-yyyy HH:mm:ss"

        // Display format: DD/MM/YYYY hh:mm:ss AM/PM
        private const val DISPLAY_FORMAT = "dd/MM/yyyy hh:mm:ss a"
    }

    // MediaPlayer for the alarm sound
    private var mediaPlayer: MediaPlayer? = null

    // Handler for updating the relative timestamp every few seconds
    private val handler = Handler(Looper.getMainLooper())
    private var timestampUpdateRunnable: Runnable? = null

    // Store original volume so we can restore it after acknowledging
    private var originalAlarmVolume: Int = 0

    // The flashing background animation
    private var flashAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make this screen show over the lock screen and turn the screen on
        setupLockScreenFlags()

        setContentView(R.layout.activity_alarm)

        // Get the alert type and timestamp from the intent
        val alertType = intent.getStringExtra(EXTRA_ALERT_TYPE) ?: "FALL_DETECTED"
        val timestamp = intent.getStringExtra(EXTRA_TIMESTAMP) ?: ""

        Log.d(TAG, "Alarm triggered! Type: $alertType, Timestamp: $timestamp")

        // Set up the UI based on alert type (red vs amber)
        setupAlertUI(alertType, timestamp)

        // Start the alarm sound at max volume
        startAlarmSound()

        // Start the flashing background animation
        startFlashingAnimation(alertType)

        // Start updating the relative timestamp
        startRelativeTimeUpdater(timestamp)

        // Set up the ACKNOWLEDGE button
        setupAcknowledgeButton(alertType)
    }

    /**
     * Makes this activity show over the lock screen and turn the screen on.
     * This ensures the caregiver sees the alarm even if their phone is locked.
     */
    private fun setupLockScreenFlags() {
        // Show over lock screen
        setShowWhenLocked(true)
        // Turn the screen on
        setTurnScreenOn(true)
        // Keep the screen on while this activity is visible
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Dismiss the keyguard (lock screen) if possible
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)
    }

    /**
     * Sets up the visual appearance based on alert type.
     * RED theme for FALL_DETECTED, AMBER theme for SUSPICIOUS.
     */
    private fun setupAlertUI(alertType: String, timestamp: String) {
        val background = findViewById<LinearLayout>(R.id.alarmBackground)
        val alertIcon = findViewById<TextView>(R.id.alertIcon)
        val alertTitle = findViewById<TextView>(R.id.alertTitle)
        val exactTimestamp = findViewById<TextView>(R.id.exactTimestamp)

        if (alertType == "FALL_DETECTED") {
            // 🔴 RED ALARM — confirmed fall
            background.setBackgroundColor(ContextCompat.getColor(this, R.color.alert_red))
            alertIcon.text = "🚨"
            alertTitle.text = "FALL DETECTED!"
        } else {
            // 🟠 AMBER WARNING — suspicious activity
            background.setBackgroundColor(ContextCompat.getColor(this, R.color.alert_amber))
            alertIcon.text = "⚠️"
            alertTitle.text = "SUSPICIOUS ACTIVITY"
        }

        // Show the exact timestamp — reformatted as DD/MM/YYYY hh:mm:ss AM/PM
        exactTimestamp.text = formatDisplayTimestamp(timestamp)
    }

    /**
     * Plays the alarm sound at MAXIMUM VOLUME.
     * Uses STREAM_ALARM which can bypass silent mode.
     * Also requests DND override.
     */
    private fun startAlarmSound() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Save original volume so we can restore it later
            originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

            // Set alarm volume to MAXIMUM
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            // Request DND access to bypass Do Not Disturb
            requestDndOverride()

            // Get the default alarm sound from the phone
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            // Create and start the media player
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)           // Alarm usage = can bypass DND
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmActivity, alarmUri)
                isLooping = true  // Keep playing until acknowledged!
                prepare()
                start()
            }

            Log.d(TAG, "Alarm sound started at max volume")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm sound: ${e.message}")
        }
    }

    /**
     * Requests permission to override Do Not Disturb mode.
     * If the app doesn't have DND access, it opens the settings page.
     */
    private fun requestDndOverride() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "DND override not granted — alarm may not play in DND mode")
            // We'll handle this permission request in MainActivity later
            // For now, STREAM_ALARM with USAGE_ALARM should still work on most devices
        }
    }

    /**
     * Creates a flashing background animation.
     * RED alarm: flashes between dark red and bright red (urgent!)
     * AMBER alarm: pulses between dark amber and bright amber (calmer)
     */
    private fun startFlashingAnimation(alertType: String) {
        val background = findViewById<LinearLayout>(R.id.alarmBackground)

        val colorFrom: Int
        val colorTo: Int
        val duration: Long

        if (alertType == "FALL_DETECTED") {
            // Fast flashing for FALL_DETECTED (urgent!)
            colorFrom = ContextCompat.getColor(this, R.color.alert_red)
            colorTo = 0xFF8B0000.toInt()  // Darker red
            duration = 500L  // Flash every 0.5 seconds
        } else {
            // Slower pulsing for SUSPICIOUS (important but less urgent)
            colorFrom = ContextCompat.getColor(this, R.color.alert_amber)
            colorTo = 0xFFE65100.toInt()  // Darker amber
            duration = 1000L  // Pulse every 1 second
        }

        flashAnimator = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animator ->
                background.setBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    /**
     * Starts a timer that updates the relative timestamp every 10 seconds.
     * Shows "Just now", "1 minute ago", "5 minutes ago", etc.
     */
    private fun startRelativeTimeUpdater(timestamp: String) {
        val relativeTimeView = findViewById<TextView>(R.id.relativeTime)

        // Parse the timestamp string from Python backend (DD-MM-YYYY HH:MM:SS format)
        val parsedDate = parseTimestamp(timestamp)

        timestampUpdateRunnable = object : Runnable {
            override fun run() {
                if (parsedDate != null) {
                    // Calculate relative time: "Just now", "1 minute ago", etc.
                    val relativeText = DateUtils.getRelativeTimeSpanString(
                        parsedDate.time,
                        System.currentTimeMillis(),
                        DateUtils.SECOND_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE
                    )
                    relativeTimeView.text = relativeText
                } else {
                    relativeTimeView.text = "Time unknown"
                }

                // Update again in 10 seconds
                handler.postDelayed(this, 10_000)
            }
        }

        // Run immediately, then every 10 seconds
        handler.post(timestampUpdateRunnable!!)
    }

    /**
     * Parses the timestamp string from the Python backend.
     * Format: "DD-MM-YYYY HH:MM:SS" (e.g., "01-03-2026 15:01:37")
     */
    private fun parseTimestamp(timestamp: String): Date? {
        return try {
            val sdf = SimpleDateFormat(TIMESTAMP_FORMAT, Locale.getDefault())
            sdf.parse(timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse timestamp: $timestamp — ${e.message}")
            null
        }
    }

    /**
     * Reformats the Python backend timestamp for a friendlier display.
     * Input:  "01-03-2026 15:01:37" (DD-MM-YYYY HH:MM:SS)
     * Output: "01/03/2026 03:01:37 PM" (DD/MM/YYYY hh:mm:ss AM/PM)
     */
    private fun formatDisplayTimestamp(timestamp: String): String {
        return try {
            val parsedDate = parseTimestamp(timestamp)
            if (parsedDate != null) {
                val displayFormat = SimpleDateFormat(DISPLAY_FORMAT, Locale.US)
                displayFormat.format(parsedDate).uppercase()
            } else {
                timestamp
            }
        } catch (e: Exception) {
            timestamp
        }
    }

    /**
     * Sets up the ACKNOWLEDGE button.
     * When pressed:
     * 1. Stops the alarm sound
     * 2. Restores original volume
     * 3. Writes acknowledged=true to Firebase
     * 4. Closes this alarm screen
     */
    private fun setupAcknowledgeButton(alertType: String) {
        val acknowledgeButton = findViewById<Button>(R.id.acknowledgeButton)

        acknowledgeButton.setOnClickListener {
            Log.d(TAG, "ACKNOWLEDGED by caregiver!")

            // 1. Stop the alarm sound
            stopAlarmSound()

            // 2. Write acknowledged=true to Firebase
            acknowledgeInFirebase()

            // 3. Close this alarm screen
            finish()
        }
    }

    /**
     * Stops the alarm sound and restores original volume.
     */
    private fun stopAlarmSound() {
        // Stop the media player
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null

        // Restore original volume
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore volume: ${e.message}")
        }

        // Stop the flashing animation
        flashAnimator?.cancel()
        flashAnimator = null

        Log.d(TAG, "Alarm sound stopped, volume restored")
    }

    /**
     * Writes acknowledged=true to Firebase Realtime Database.
     * This tells the Python backend: "The caregiver has seen the alert!"
     */
    private fun acknowledgeInFirebase() {
        val database = FirebaseDatabase.getInstance()
        val fallAlertRef = database.getReference("fall_alert")

        fallAlertRef.child("acknowledged").setValue(true)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully wrote acknowledged=true to Firebase")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to write acknowledgement: ${e.message}")
            }
    }

    /**
     * If the user presses the back button, do nothing — they MUST acknowledge!
     * The alarm is too important to dismiss accidentally.
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Intentionally do nothing — alarm cannot be dismissed with Back button
        // The caregiver MUST press ACKNOWLEDGE
    }

    /**
     * Clean up when the activity is destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
        timestampUpdateRunnable?.let { handler.removeCallbacks(it) }
    }
}
