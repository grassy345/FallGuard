package com.fallguard.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * FullHistoryActivity — Shows ALL fall events (no 10-record limit).
 * Opened from the "MORE" button on the dashboard.
 */
class FullHistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_history)

        // Force dark status bar with white icons
        window.statusBarColor = getColor(R.color.primary_dark)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()

        // Toolbar with back button
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // RecyclerView with ALL events
        val recyclerView = findViewById<RecyclerView>(R.id.fullHistoryRecycler)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val adapter = FallEventAdapter(
            onThumbnailClick = { event -> openVideoPlayer(event) },
            onSaveClick = { event -> saveVideo(event) }
        )
        recyclerView.adapter = adapter

        // Observe ALL fall events from Room
        val dao = FallDatabase.getInstance(this).fallEventDao()
        dao.getAll().observe(this) { events ->
            adapter.submitList(events)
        }
    }

    private fun openVideoPlayer(event: FallEvent) {
        if (event.clipUrl.isNullOrEmpty()) {
            Toast.makeText(this, "Video not yet available", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, VideoPlayerActivity::class.java).apply {
            putExtra("clip_url", event.clipUrl)
            putExtra("timestamp", event.timestamp)
            putExtra("fall_status", event.fallStatus)
        }
        startActivity(intent)
    }

    private fun saveVideo(event: FallEvent) {
        if (event.clipUrl.isNullOrEmpty()) {
            Toast.makeText(this, "Video not yet available", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("fallguard_settings", MODE_PRIVATE)
        val saveDir = prefs.getString("save_location", null)
            ?: File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "FallGuard").absolutePath

        val fileName = "Fall_${event.timestamp.replace(":", "-").replace(" ", "_")}.mp4"
        val targetFile = File(saveDir, fileName)

        Toast.makeText(this, "Downloading video...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                File(saveDir).mkdirs()
                val connection = URL(event.clipUrl).openConnection()
                connection.connect()
                val input = connection.getInputStream()
                val output = FileOutputStream(targetFile)
                input.copyTo(output)
                output.close()
                input.close()
                runOnUiThread {
                    Toast.makeText(this@FullHistoryActivity,
                        "Video saved: ${targetFile.name}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@FullHistoryActivity,
                        "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
