package com.example.soundwave.data

import android.content.Context
import androidx.room.Room
import com.example.soundwave.data.database.SoundWaveDatabase
import com.example.soundwave.data.repository.MusicRepository
import com.example.soundwave.data.repository.PlaylistRepository

object AppDatabaseModule {
    private var database: SoundWaveDatabase? = null
    
    fun getDatabase(context: Context): SoundWaveDatabase {
        if (database == null) {
            database = Room.databaseBuilder(
                context.applicationContext,
                SoundWaveDatabase::class.java,
                "soundwave_database"
            ).build()
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
}



