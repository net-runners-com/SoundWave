package com.example.soundwave.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.soundwave.data.database.SoundWaveDatabase
import com.example.soundwave.data.repository.LyricsRepository
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
    
    fun getDatabase(context: Context): SoundWaveDatabase {
        if (database == null) {
            database = Room.databaseBuilder(
                context.applicationContext,
                SoundWaveDatabase::class.java,
                "soundwave_database"
            )
            .addMigrations(MIGRATION_1_2)
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
}



