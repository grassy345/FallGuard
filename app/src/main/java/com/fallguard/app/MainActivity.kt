package com.fallguard.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * MainActivity — The FallGuard Dashboard.
 *
 * Shows a toolbar with drawer, monitoring status, and a list of
 * recent fall events as compact cards with video thumbnails.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val NOTIFICATION_PERMISSION_CODE = 100
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var adapter: FallEventAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Guard: If not logged in, redirect to login screen
        if (auth.currentUser == null) {
            navigateToLogin()
            return
        }

        setContentView(R.layout.activity_main)

        // Set up Toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Set up Drawer
        drawerLayout = findViewById(R.id.drawerLayout)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.app_name, R.string.app_name
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        toggle.drawerArrowDrawable.color = ContextCompat.getColor(this, R.color.white)

        // Set up NavigationView
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        setupDrawer(navigationView)

        // Fix drawer header: add top padding equal to status bar height
        // so the app icon/text doesn't go behind the system status bar
        val headerView = navigationView.getHeaderView(0)
        val statusBarHeight = getStatusBarHeight()
        headerView.setPadding(
            headerView.paddingLeft,
            headerView.paddingTop + statusBarHeight,
            headerView.paddingRight,
            headerView.paddingBottom
        )

        // Keep status bar dark when drawer is open
        drawerLayout.setStatusBarBackgroundColor(
            ContextCompat.getColor(this, R.color.primary_dark)
        )

        // Show user email in status bar
        val userEmailBadge = findViewById<TextView>(R.id.userEmailBadge)
        userEmailBadge.text = auth.currentUser?.email ?: ""

        // Set up RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.fallEventsRecycler)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = FallEventAdapter(
            onThumbnailClick = { event -> openVideoPlayer(event) },
            onSaveClick = { event -> saveVideo(event) },
            onLongPress = { event -> confirmDeleteEvent(event) }
        )
        recyclerView.adapter = adapter

        // Observe fall events from Room database (LiveData = auto-refresh!)
        val dao = FallDatabase.getInstance(this).fallEventDao()
        dao.getLatest10().observe(this) { events ->
            val emptyState = findViewById<LinearLayout>(R.id.emptyState)
            val moreButton = findViewById<Button>(R.id.moreButton)

            if (events.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
                moreButton.visibility = View.GONE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyState.visibility = View.GONE
                adapter.submitList(events)

                // Show MORE button if there are 10 items (could be more in DB)
                moreButton.visibility = if (events.size >= 10) View.VISIBLE else View.GONE

                // Check if we need to highlight a specific card (from notification tap)
                checkAndHighlightCard(recyclerView, events)
            }
        }

        // MORE button → full history
        val moreButton = findViewById<Button>(R.id.moreButton)
        moreButton.setOnClickListener {
            startActivity(Intent(this, FullHistoryActivity::class.java))
        }

        // Request permissions and start service
        requestNotificationPermission()
        requestDndPermission()
        requestOverlayPermission()
        requestBatteryOptimizationExemption()
        startFallDetectionService()
    }

    /** Handle notification taps when activity is already running */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)  // Store new intent so checkAndHighlightCard can read it
        val recyclerView = findViewById<RecyclerView>(R.id.fallEventsRecycler)
        checkAndHighlightCard(recyclerView, adapter.currentList)
    }

    /** Scroll to and blink-highlight the card matching the notification timestamp */
    private fun checkAndHighlightCard(recyclerView: RecyclerView, events: List<FallEvent>) {
        val highlightTimestamp = intent.getStringExtra("highlight_timestamp") ?: return

        val index = events.indexOfFirst { it.timestamp == highlightTimestamp }
        if (index >= 0) {
            // Scroll to the card
            recyclerView.scrollToPosition(index)

            // Wait for layout, then apply blink animation
            recyclerView.post {
                val viewHolder = recyclerView.findViewHolderForAdapterPosition(index)
                viewHolder?.itemView?.let { card -> blinkHighlight(card) }
            }

            // Clear the extra so it doesn't re-highlight on config changes
            intent.removeExtra("highlight_timestamp")
        }
    }

    /** Blink the card 2 times with a yellow highlight flash */
    private fun blinkHighlight(view: View) {
        val highlight = android.graphics.drawable.ColorDrawable(
            ContextCompat.getColor(this, R.color.alert_amber)
        )
        val original = view.background

        val blink = object : android.os.CountDownTimer(1200, 300) {
            var count = 0
            override fun onTick(millisUntilFinished: Long) {
                view.background = if (count % 2 == 0) highlight else original
                count++
            }
            override fun onFinish() {
                view.background = original
            }
        }
        blink.start()
    }

    /** Handle toolbar menu clicks (settings gear) */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Set up the navigation drawer with menu items */
    private fun setupDrawer(navigationView: NavigationView) {
        // Set user email in drawer header
        val headerView = navigationView.getHeaderView(0)
        val drawerEmail = headerView.findViewById<TextView>(R.id.drawerUserEmail)
        drawerEmail.text = auth.currentUser?.email ?: ""

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                R.id.nav_github -> {
                    val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/grassy345/FallGuard"))
                    startActivity(intent)
                }
                R.id.nav_logout -> {
                    // Stop service, sign out, navigate to login
                    stopService(Intent(this, FallDetectionService::class.java))
                    auth.signOut()
                    navigateToLogin()
                }
            }
            drawerLayout.closeDrawers()
            true
        }
    }

    /** Launch the video player for a fall event */
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

    /** Long-press delete: shows confirmation, then removes event from Room DB */
    private fun confirmDeleteEvent(event: FallEvent) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete Event")
            .setMessage("Delete this fall event?\n${event.fallStatus} — ${event.timestamp}")
            .setPositiveButton("Delete") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    FallDatabase.getInstance(this@MainActivity).fallEventDao().deleteById(event.id)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Event deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Download and save the video to the configured save location */
    private fun saveVideo(event: FallEvent) {
        if (event.clipUrl.isNullOrEmpty()) {
            Toast.makeText(this, "Video not yet available", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("fallguard_settings", MODE_PRIVATE)
        val saveDir = prefs.getString("save_location", null)
            ?: File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "FallGuard").absolutePath

        // Create filename from timestamp: "Fall_01-03-2026_03-01-37_PM.mp4"
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
                    Toast.makeText(this@MainActivity,
                        "Video saved to: ${targetFile.name}", Toast.LENGTH_LONG).show()
                }
                Log.d(TAG, "Video saved: ${targetFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save video: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@MainActivity,
                        "Failed to save video: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun startFallDetectionService() {
        val serviceIntent = Intent(this, FallDetectionService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

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

    private fun requestDndPermission() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    /** Get the system status bar height in pixels */
    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }
}
