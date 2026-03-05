package com.fallguard.app

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * VideoPlayerActivity — Plays fall detection video clips using ExoPlayer.
 *
 * Receives a Cloudinary .mp4 URL and plays it inline.
 * Handles dead links gracefully with an error state.
 * Save button downloads the video to the configured save location.
 */
class VideoPlayerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VideoPlayerActivity"
    }

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        val clipUrl = intent.getStringExtra("clip_url") ?: ""
        val timestamp = intent.getStringExtra("timestamp") ?: ""
        val fallStatus = intent.getStringExtra("fall_status") ?: ""

        // Toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val displayStatus = if (fallStatus == "FALL_DETECTED") "Fall Detected" else "Suspicious"
        toolbar.title = "$displayStatus — $timestamp"
        toolbar.setNavigationOnClickListener { finish() }

        // Toolbar save button
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_save -> {
                    saveVideo(clipUrl, timestamp)
                    true
                }
                else -> false
            }
        }

        if (clipUrl.isEmpty()) {
            showError()
            return
        }

        // Initialize ExoPlayer
        val playerView = findViewById<PlayerView>(R.id.playerView)
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            playerView.player = exoPlayer
            val mediaItem = MediaItem.fromUri(Uri.parse(clipUrl))
            exoPlayer.setMediaItem(mediaItem)

            // Handle playback errors (dead links)
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Playback error: ${error.message}")
                    showError()
                }
            })

            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    private fun showError() {
        val playerView = findViewById<PlayerView>(R.id.playerView)
        val errorState = findViewById<LinearLayout>(R.id.errorState)
        playerView.visibility = View.GONE
        errorState.visibility = View.VISIBLE
    }

    private fun saveVideo(clipUrl: String, timestamp: String) {
        if (clipUrl.isEmpty()) {
            Toast.makeText(this, "No video to save", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("fallguard_settings", MODE_PRIVATE)
        val saveDir = prefs.getString("save_location", null)
            ?: File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "FallGuard").absolutePath

        val fileName = "Fall_${timestamp.replace(":", "-").replace(" ", "_")}.mp4"
        val targetFile = File(saveDir, fileName)

        Toast.makeText(this, "Downloading video...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                File(saveDir).mkdirs()
                val connection = URL(clipUrl).openConnection()
                connection.connect()
                val input = connection.getInputStream()
                val output = FileOutputStream(targetFile)
                input.copyTo(output)
                output.close()
                input.close()
                runOnUiThread {
                    Toast.makeText(this@VideoPlayerActivity,
                        "Video saved: ${targetFile.name}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save video: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@VideoPlayerActivity,
                        "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }
}
