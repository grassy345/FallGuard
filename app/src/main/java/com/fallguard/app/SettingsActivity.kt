package com.fallguard.app

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * SettingsActivity — FallGuard Settings Screen.
 *
 * Allows the user to:
 * - Pick alarm tone (system tones or custom .mp3)
 * - Set video save location (with automatic migration of existing files)
 * - View GitHub repo and app version
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"
        private const val PREFS_NAME = "fallguard_settings"
        private const val KEY_ALARM_TONE_URI = "alarm_tone_uri"
        private const val KEY_ALARM_TONE_NAME = "alarm_tone_name"
        private const val KEY_SAVE_LOCATION = "save_location"
    }

    // Ringtone picker result
    private val ringtonePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(
                RingtoneManager.EXTRA_RINGTONE_PICKED_URI
            )
            if (uri != null) {
                val ringtone = RingtoneManager.getRingtone(this, uri)
                val title = ringtone?.getTitle(this) ?: "Custom tone"

                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(KEY_ALARM_TONE_URI, uri.toString())
                    .putString(KEY_ALARM_TONE_NAME, title)
                    .apply()

                findViewById<TextView>(R.id.currentToneName).text = title
                Log.d(TAG, "Alarm tone set to: $title ($uri)")
            }
        }
    }

    // Custom .mp3 file picker result
    private val filePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist permission so we can access the file later
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't take persistable permission: ${e.message}")
            }

            val toneName = "Custom audio file"
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_ALARM_TONE_URI, uri.toString())
                .putString(KEY_ALARM_TONE_NAME, toneName)
                .apply()

            findViewById<TextView>(R.id.currentToneName).text = toneName
            Log.d(TAG, "Custom alarm tone set: $uri")
        }
    }

    // Directory picker for save location
    private val directoryPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Convert content URI to actual file path
            val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                uri, android.provider.DocumentsContract.getTreeDocumentId(uri)
            )
            val actualPath = getFilePathFromUri(docUri)

            if (actualPath != null) {
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                val oldLocation = prefs.getString(KEY_SAVE_LOCATION, null)

                prefs.edit().putString(KEY_SAVE_LOCATION, actualPath).apply()
                findViewById<TextView>(R.id.currentSaveLocation).text = actualPath

                // Migrate existing videos from old location to new
                val effectiveOldLocation = oldLocation ?: getDefaultSaveDir()
                if (effectiveOldLocation != actualPath) {
                    migrateVideos(effectiveOldLocation, actualPath)
                }

                Log.d(TAG, "Save location set to: $actualPath")
            } else {
                Toast.makeText(this, "Could not resolve directory path", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Force dark status bar with white icons
        // (Light theme auto-sets dark icons which are unreadable on light background)
        window.statusBarColor = getColor(R.color.primary_dark)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()

        // Toolbar with back button
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Show current alarm tone name
        val toneName = prefs.getString(KEY_ALARM_TONE_NAME, "Default alarm tone")
        findViewById<TextView>(R.id.currentToneName).text = toneName

        // Show current save location
        val saveLocation = prefs.getString(KEY_SAVE_LOCATION, null)
        findViewById<TextView>(R.id.currentSaveLocation).text =
            saveLocation ?: getDefaultSaveDir()

        // Alarm Tone card — show picker dialog
        findViewById<MaterialCardView>(R.id.alarmToneCard).setOnClickListener {
            showTonePickerDialog()
        }

        // Save Location card — show options dialog
        findViewById<MaterialCardView>(R.id.saveLocationCard).setOnClickListener {
            showSaveLocationDialog()
        }

        // GitHub card — open in browser
        findViewById<MaterialCardView>(R.id.githubCard).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/grassy345/FallGuard"))
            startActivity(intent)
        }
    }

    /** Show a dialog to choose between system tones and custom file */
    private fun showTonePickerDialog() {
        val options = arrayOf("Choose from system tones", "Pick custom audio file (.mp3)")
        android.app.AlertDialog.Builder(this)
            .setTitle("Select Alarm Tone")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openSystemTonePicker()
                    1 -> filePicker.launch("audio/*")
                }
            }
            .show()
    }

    /** Open the system ringtone/alarm tone picker */
    private fun openSystemTonePicker() {
        val currentUri = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_ALARM_TONE_URI, null)

        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Tone")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            if (currentUri != null) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentUri))
            }
        }
        ringtonePicker.launch(intent)
    }

    /** Move existing video files from old location to new location */
    private fun migrateVideos(oldPath: String, newPath: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val oldDir = File(oldPath)
                if (!oldDir.exists() || !oldDir.isDirectory) return@launch

                val videoFiles = oldDir.listFiles { file ->
                    file.name.startsWith("Fall_") && file.extension.equals("mp4", ignoreCase = true)
                } ?: return@launch

                if (videoFiles.isEmpty()) return@launch

                val newDir = File(newPath)
                newDir.mkdirs()

                var movedCount = 0
                for (file in videoFiles) {
                    try {
                        val newFile = File(newDir, file.name)
                        file.copyTo(newFile, overwrite = true)
                        file.delete()
                        movedCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to move ${file.name}: ${e.message}")
                    }
                }

                runOnUiThread {
                    Toast.makeText(this@SettingsActivity,
                        "$movedCount video(s) moved to new location",
                        Toast.LENGTH_LONG).show()
                }
                Log.d(TAG, "Migrated $movedCount videos from $oldPath to $newPath")
            } catch (e: Exception) {
                Log.e(TAG, "Migration failed: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@SettingsActivity,
                        "Failed to migrate videos: ${e.message}",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** Show dialog: Choose directory or Reset to Default */
    private fun showSaveLocationDialog() {
        val options = arrayOf("Choose directory", "Reset to default (Downloads/FallGuard)")
        android.app.AlertDialog.Builder(this)
            .setTitle("Video Save Location")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> directoryPicker.launch(null)
                    1 -> {
                        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        prefs.edit().remove(KEY_SAVE_LOCATION).apply()
                        findViewById<TextView>(R.id.currentSaveLocation).text = getDefaultSaveDir()
                        Toast.makeText(this, "Reset to default save location", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    /** Default save directory path */
    private fun getDefaultSaveDir(): String {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "FallGuard"
        ).absolutePath
    }

    /** Convert a content:// URI to an actual file path */
    private fun getFilePathFromUri(uri: Uri): String? {
        return try {
            val docId = android.provider.DocumentsContract.getDocumentId(uri)
            val split = docId.split(":")
            val type = split[0]
            if ("primary".equals(type, ignoreCase = true)) {
                "${Environment.getExternalStorageDirectory()}/${split[1]}"
            } else {
                // External SD card or other storage
                "/storage/$type/${split.getOrElse(1) { "" }}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file path from URI: ${e.message}")
            null
        }
    }
}
