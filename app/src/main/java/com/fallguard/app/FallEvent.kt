package com.fallguard.app

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * FallEvent — A single fall detection event stored in the local database.
 *
 * Each time the service detects a fall (FALL_DETECTED or SUSPICIOUS),
 * it creates a FallEvent and saves it locally. The clipUrl gets updated
 * a few seconds later when the Python backend finishes uploading the video.
 */
@Entity(tableName = "fall_events")
data class FallEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** "FALL_DETECTED" or "SUSPICIOUS" */
    val fallStatus: String,

    /** Raw timestamp from Python backend: "DD-MM-YYYY HH:MM:SS" */
    val timestamp: String,

    /** Whether the caregiver pressed ACKNOWLEDGE */
    val acknowledged: Boolean = false,

    /** Cloudinary .mp4 URL — null until Python finishes uploading */
    val clipUrl: String? = null,

    /** When this event was saved locally (milliseconds since epoch) */
    val savedAt: Long = System.currentTimeMillis()
)
