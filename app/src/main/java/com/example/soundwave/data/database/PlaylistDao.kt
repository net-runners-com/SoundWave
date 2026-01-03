package com.example.soundwave.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY dateModified DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>
    
    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?
    
    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long
    
    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)
    
    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)
    
    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :playlistId ORDER BY ps.position ASC")
    fun getSongsInPlaylist(playlistId: Long): Flow<List<SongEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(playlistSong: PlaylistSongEntity)
    
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)
    
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)
}



