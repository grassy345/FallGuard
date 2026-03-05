package com.fallguard.app

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * FallEventDao — Database queries for fall events.
 *
 * Room generates the actual SQL implementation at compile time.
 * We just define what we need using annotations.
 */
@Dao
interface FallEventDao {

    /** Insert a new fall event. Returns the auto-generated ID. */
    @Insert
    suspend fun insert(event: FallEvent): Long

    /** Get the latest 10 events (for the dashboard). LiveData = auto-refreshes UI. */
    @Query("SELECT * FROM fall_events ORDER BY savedAt DESC LIMIT 10")
    fun getLatest10(): LiveData<List<FallEvent>>

    /** Get ALL events (for the full history screen). */
    @Query("SELECT * FROM fall_events ORDER BY savedAt DESC")
    fun getAll(): LiveData<List<FallEvent>>

    /** Update the clip URL for a specific event (when clip finishes uploading). */
    @Query("UPDATE fall_events SET clipUrl = :clipUrl WHERE id = :eventId")
    suspend fun updateClipUrl(eventId: Long, clipUrl: String)

    /** Get the most recent event (to update its clipUrl when it arrives). */
    @Query("SELECT * FROM fall_events ORDER BY savedAt DESC LIMIT 1")
    suspend fun getMostRecent(): FallEvent?

    /** Delete a specific event by ID (dev feature). */
    @Query("DELETE FROM fall_events WHERE id = :eventId")
    suspend fun deleteById(eventId: Long)
}
