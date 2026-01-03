package com.example.soundwave.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SoundWaveDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
}

