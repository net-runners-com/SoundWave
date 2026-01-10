package com.example.soundwave.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.soundwave.data.database.SongDao
import com.example.soundwave.data.database.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class MusicRepository(
    private val songDao: SongDao,
    private val context: Context
) {
    fun getAllSongs(): Flow<List<SongEntity>> = songDao.getAllSongs()
    
    fun getSongsByAlbum(album: String): Flow<List<SongEntity>> = songDao.getSongsByAlbum(album)
    
    fun getSongsByArtist(artist: String): Flow<List<SongEntity>> = songDao.getSongsByArtist(artist)
    
    fun getSongsByFolder(folderPath: String): Flow<List<SongEntity>> = songDao.getSongsByFolder(folderPath)
    
    fun getAllAlbums(): Flow<List<String>> = songDao.getAllAlbums()
    
    fun getAllArtists(): Flow<List<String>> = songDao.getAllArtists()
    
    // フォルダ一覧を取得（filePathから親ディレクトリを抽出）
    fun getAllFolders(): Flow<List<String>> = songDao.getAllFilePaths().map { filePaths ->
        filePaths.mapNotNull { filePath ->
            val file = File(filePath)
            file.parent ?: null
        }.distinct().sorted()
    }
    
    fun searchSongs(query: String): Flow<List<SongEntity>> = songDao.searchSongs(query)
    
    // Paging対応: 大量データを効率的に読み込む
    fun getAllSongsPaged(scope: CoroutineScope): Flow<PagingData<SongEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                enablePlaceholders = false,
                prefetchDistance = 10,
                initialLoadSize = 50
            ),
            pagingSourceFactory = { songDao.getAllSongsPaged() }
        ).flow.cachedIn(scope)
    }
    
    // 最適化: 最近の曲を取得
    suspend fun getRecentSongs(limit: Int = 50) = withContext(Dispatchers.IO) {
        songDao.getRecentSongs(limit)
    }
    
    // 最適化: 人気の曲を取得
    suspend fun getPopularSongs(limit: Int = 50) = withContext(Dispatchers.IO) {
        songDao.getPopularSongs(limit)
    }
    
    suspend fun scanMusicFiles() = withContext(Dispatchers.IO) {
        val songs = mutableListOf<SongEntity>()
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.DATE_ADDED
        )
        
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        
        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val trackColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val yearColumn = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val genreColumn = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val duration = cursor.getLong(durationColumn)
                val filePath = cursor.getString(dataColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val track = if (trackColumn >= 0) cursor.getInt(trackColumn) else null
                val year = if (yearColumn >= 0) cursor.getInt(yearColumn) else null
                val genre = if (genreColumn >= 0) cursor.getString(genreColumn) else null
                val dateAdded = cursor.getLong(dateAddedColumn) * 1000 // Convert to milliseconds
                
                // Get album art
                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )
                val albumArtPath = albumArtUri.toString()
                
                val file = File(filePath)
                if (file.exists()) {
                    // ファイルの最終更新時刻を取得
                    val fileLastModified = try {
                        file.lastModified()
                    } catch (e: Exception) {
                        null
                    }
                    
                    songs.add(
                        SongEntity(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            genre = genre,
                            duration = duration,
                            filePath = filePath,
                            albumArtPath = albumArtPath,
                            trackNumber = track,
                            year = year,
                            dateAdded = dateAdded,
                            fileLastModified = fileLastModified,
                            playCount = 0,
                            lastPlayed = null
                        )
                    )
                }
            }
        }
        
        if (songs.isNotEmpty()) {
            songDao.insertSongs(songs)
        }
    }
    
    suspend fun getSongById(id: Long) = songDao.getSongById(id)
    
    // すべての曲を同期で取得（全曲ループ用）
    suspend fun getAllSongsSync(): List<SongEntity> = withContext(Dispatchers.IO) {
        getAllSongs().first()
    }
    
    suspend fun incrementPlayCount(id: Long) {
        songDao.incrementPlayCount(id, System.currentTimeMillis())
    }
    
    suspend fun updateSong(song: SongEntity) = withContext(Dispatchers.IO) {
        songDao.updateSong(song)
    }
}



