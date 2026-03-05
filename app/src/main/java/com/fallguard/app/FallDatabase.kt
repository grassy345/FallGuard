package com.fallguard.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * FallDatabase — The local Room database for FallGuard.
 *
 * Stores fall event history so the dashboard can display past events
 * even when offline. Uses the singleton pattern — only one instance exists.
 */
@Database(entities = [FallEvent::class], version = 1, exportSchema = false)
abstract class FallDatabase : RoomDatabase() {

    abstract fun fallEventDao(): FallEventDao

    companion object {
        @Volatile
        private var INSTANCE: FallDatabase? = null

        /** Get the singleton database instance. Creates it on first call. */
        fun getInstance(context: Context): FallDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FallDatabase::class.java,
                    "fallguard_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
