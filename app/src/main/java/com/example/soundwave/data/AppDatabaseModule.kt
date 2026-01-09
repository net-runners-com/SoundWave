package com.example.soundwave.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.soundwave.data.database.SoundWaveDatabase
import com.example.soundwave.data.repository.LyricsRepository
import com.example.soundwave.data.repository.LocationCircleRepository
import com.example.soundwave.data.repository.MusicRepository
import com.example.soundwave.data.repository.PlaylistRepository

object AppDatabaseModule {
    private var database: SoundWaveDatabase? = null
    
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS lyrics (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    songId INTEGER NOT NULL,
                    lyricsText TEXT NOT NULL,
                    lyricsLrc TEXT,
                    source TEXT,
                    dateAdded INTEGER NOT NULL,
                    dateModified INTEGER NOT NULL,
                    FOREIGN KEY(songId) REFERENCES songs(id) ON DELETE CASCADE
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_lyrics_songId ON lyrics(songId)")
        }
    }
    
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS location_circles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    radius REAL NOT NULL,
                    playlistId INTEGER,
                    dateCreated INTEGER NOT NULL,
                    dateModified INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }
    
    fun getDatabase(context: Context): SoundWaveDatabase {
        if (database == null) {
            database = Room.databaseBuilder(
                context.applicationContext,
                SoundWaveDatabase::class.java,
                "soundwave_database"
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        }
        return database!!
    }
    
    fun getMusicRepository(context: Context): MusicRepository {
        val db = getDatabase(context)
        return MusicRepository(db.songDao(), context)
    }
    
    fun getPlaylistRepository(context: Context): PlaylistRepository {
        val db = getDatabase(context)
        return PlaylistRepository(db.playlistDao())
    }
    
    fun getLyricsRepository(context: Context): LyricsRepository {
        val db = getDatabase(context)
        return LyricsRepository(db.lyricsDao(), context)
    }
    
    fun getLocationCircleRepository(context: Context): LocationCircleRepository {
        val db = getDatabase(context)
        return LocationCircleRepository(db.locationCircleDao())
    }
}



