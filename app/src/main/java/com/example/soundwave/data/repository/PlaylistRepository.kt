package com.example.soundwave.data.repository

import com.example.soundwave.data.database.PlaylistDao
import com.example.soundwave.data.database.PlaylistEntity
import com.example.soundwave.data.database.PlaylistSongEntity
import com.example.soundwave.data.database.SongEntity
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(
    private val playlistDao: PlaylistDao
) {
    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()
    
    suspend fun getPlaylistById(id: Long) = playlistDao.getPlaylistById(id)
    
    suspend fun createPlaylist(name: String): Long {
        val now = System.currentTimeMillis()
        val playlist = PlaylistEntity(
            name = name,
            dateCreated = now,
            dateModified = now
        )
        return playlistDao.insertPlaylist(playlist)
    }
    
    suspend fun updatePlaylist(playlist: PlaylistEntity) {
        playlistDao.updatePlaylist(playlist.copy(dateModified = System.currentTimeMillis()))
    }
    
    suspend fun deletePlaylist(playlist: PlaylistEntity) {
        playlistDao.deletePlaylist(playlist)
    }
    
    fun getSongsInPlaylist(playlistId: Long): Flow<List<SongEntity>> {
        return playlistDao.getSongsInPlaylist(playlistId)
    }
    
    suspend fun addSongToPlaylist(playlistId: Long, songId: Long, position: Int) {
        val playlistSong = PlaylistSongEntity(
            playlistId = playlistId,
            songId = songId,
            position = position
        )
        playlistDao.insertPlaylistSong(playlistSong)
    }
    
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }
}

