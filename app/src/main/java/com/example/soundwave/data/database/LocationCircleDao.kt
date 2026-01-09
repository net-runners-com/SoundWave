package com.example.soundwave.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationCircleDao {
    @Query("SELECT * FROM location_circles ORDER BY dateModified DESC")
    fun getAllCircles(): Flow<List<LocationCircleEntity>>
    
    @Query("SELECT * FROM location_circles WHERE id = :id")
    suspend fun getCircleById(id: Long): LocationCircleEntity?
    
    @Query("SELECT * FROM location_circles")
    suspend fun getAllCirclesSync(): List<LocationCircleEntity>
    
    @Insert
    suspend fun insertCircle(circle: LocationCircleEntity): Long
    
    @Update
    suspend fun updateCircle(circle: LocationCircleEntity)
    
    @Delete
    suspend fun deleteCircle(circle: LocationCircleEntity)
    
    @Query("DELETE FROM location_circles WHERE id = :id")
    suspend fun deleteCircleById(id: Long)
}

