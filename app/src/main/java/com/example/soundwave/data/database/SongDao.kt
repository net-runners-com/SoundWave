package com.example.soundwave.data.database

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<SongEntity>>
    
    // Paging対応のクエリ
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongsPaged(): PagingSource<Int, SongEntity>
    
    // 最適化: 最近の曲を取得（LIMIT付き）
    @Query("SELECT * FROM songs ORDER BY lastPlayed DESC, dateAdded DESC LIMIT :limit")
    suspend fun getRecentSongs(limit: Int = 50): List<SongEntity>
    
    // 最適化: 人気の曲を取得
    @Query("SELECT * FROM songs ORDER BY playCount DESC LIMIT :limit")
    suspend fun getPopularSongs(limit: Int = 50): List<SongEntity>
    
    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): SongEntity?
    
    @Query("SELECT * FROM songs WHERE album = :album ORDER BY trackNumber ASC, title ASC")
    fun getSongsByAlbum(album: String): Flow<List<SongEntity>>
    
    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album ASC, trackNumber ASC")
    fun getSongsByArtist(artist: String): Flow<List<SongEntity>>
    
    @Query("SELECT DISTINCT album FROM songs WHERE artist = :artist")
    fun getAlbumsByArtist(artist: String): Flow<List<String>>
    
    @Query("SELECT DISTINCT album FROM songs")
    fun getAllAlbums(): Flow<List<String>>
    
    @Query("SELECT DISTINCT artist FROM songs")
    fun getAllArtists(): Flow<List<String>>
    
    // フォルダ一覧を取得（すべての曲を取得してKotlin側で処理）
    @Query("SELECT DISTINCT filePath FROM songs WHERE filePath IS NOT NULL AND filePath != ''")
    fun getAllFilePaths(): Flow<List<String>>
    
    @Query("SELECT * FROM songs WHERE filePath LIKE :folderPath || '%'")
    fun getSongsByFolder(folderPath: String): Flow<List<SongEntity>>
    
    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<SongEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)
    
    @Update
    suspend fun updateSong(song: SongEntity)
    
    @Delete
    suspend fun deleteSong(song: SongEntity)
    
    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayed = :timestamp WHERE id = :id")
    suspend fun incrementPlayCount(id: Long, timestamp: Long)
}



