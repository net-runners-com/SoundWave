package com.example.soundwave.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_circles")
data class LocationCircleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Double, // メートル単位
    val playlistId: Long?, // nullの場合は最新曲を再生
    val dateCreated: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis()
)

