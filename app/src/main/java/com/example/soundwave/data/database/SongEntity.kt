package com.example.soundwave.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String?,
    val duration: Long, // milliseconds
    val filePath: String,
    val albumArtPath: String?,
    val trackNumber: Int?,
    val year: Int?,
    val dateAdded: Long, // timestamp
    val playCount: Int = 0,
    val lastPlayed: Long? = null
)



