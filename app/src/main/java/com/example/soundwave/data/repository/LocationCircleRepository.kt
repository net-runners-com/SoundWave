package com.example.soundwave.data.repository

import com.example.soundwave.data.database.LocationCircleDao
import com.example.soundwave.data.database.LocationCircleEntity
import kotlinx.coroutines.flow.Flow

class LocationCircleRepository(
    private val locationCircleDao: LocationCircleDao
) {
    fun getAllCircles(): Flow<List<LocationCircleEntity>> = locationCircleDao.getAllCircles()
    
    suspend fun getCircleById(id: Long) = locationCircleDao.getCircleById(id)
    
    suspend fun getAllCirclesSync() = locationCircleDao.getAllCirclesSync()
    
    suspend fun createCircle(
        name: String,
        latitude: Double,
        longitude: Double,
        radius: Double,
        playlistId: Long?
    ): Long {
        val now = System.currentTimeMillis()
        val circle = LocationCircleEntity(
            name = name,
            latitude = latitude,
            longitude = longitude,
            radius = radius,
            playlistId = playlistId,
            dateCreated = now,
            dateModified = now
        )
        return locationCircleDao.insertCircle(circle)
    }
    
    suspend fun updateCircle(circle: LocationCircleEntity) {
        locationCircleDao.updateCircle(circle.copy(dateModified = System.currentTimeMillis()))
    }
    
    suspend fun deleteCircle(circle: LocationCircleEntity) {
        locationCircleDao.deleteCircle(circle)
    }
    
    suspend fun deleteCircleById(id: Long) {
        locationCircleDao.deleteCircleById(id)
    }
}

