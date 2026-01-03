package com.example.soundwave.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE songId = :songId LIMIT 1")
    suspend fun getLyricsBySongId(songId: Long): LyricsEntity?
    
    @Query("SELECT * FROM lyrics WHERE songId = :songId LIMIT 1")
    fun getLyricsBySongIdFlow(songId: Long): Flow<LyricsEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(lyrics: LyricsEntity): Long
    
    @Update
    suspend fun updateLyrics(lyrics: LyricsEntity)
    
    @Delete
    suspend fun deleteLyrics(lyrics: LyricsEntity)
    
    @Query("DELETE FROM lyrics WHERE songId = :songId")
    suspend fun deleteLyricsBySongId(songId: Long)
}

