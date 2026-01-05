package com.example.soundwave.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.soundwave.data.AppDatabaseModule
import com.example.soundwave.player.PlayerManager
import com.example.soundwave.ui.permission.PermissionScreen
import com.example.soundwave.ui.album.AlbumDetailScreen
import com.example.soundwave.ui.artist.ArtistDetailScreen
import com.example.soundwave.ui.folder.FolderDetailScreen
import com.example.soundwave.ui.playlist.PlaylistDetailScreen
import com.example.soundwave.ui.home.HomeScreen
import com.example.soundwave.ui.player.PlayerScreen
import com.example.soundwave.ui.settings.SettingsScreen
import com.example.soundwave.ui.settings.WidgetSettingsScreen
import com.example.soundwave.ui.song.SongDetailScreen
import com.example.soundwave.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

@Composable
fun SoundWaveNavigation(
    onThemeChanged: (AppTheme) -> Unit = {}
) {
    val context = LocalContext.current
    val playerManager = remember { PlayerManager.getInstance(context) }
    val musicRepository = remember { AppDatabaseModule.getMusicRepository(context) }
    val playlistRepository = remember { AppDatabaseModule.getPlaylistRepository(context) }
    
    var hasPermissions by remember { mutableStateOf(false) }
    var currentSongId by remember { mutableStateOf<Long?>(null) }
    var currentSongDetailId by remember { mutableStateOf<Long?>(null) }
    var currentAlbum by remember { mutableStateOf<String?>(null) }
    var currentArtist by remember { mutableStateOf<String?>(null) }
    var currentFolderPath by remember { mutableStateOf<String?>(null) }
    var currentPlaylistId by remember { mutableStateOf<Long?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showWidgetSettings by remember { mutableStateOf(false) }
    
    // 曲をクリックしたときの処理（再生のみ、画面遷移しない）
    val onSongClick: (Long) -> Unit = { songId ->
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val song = musicRepository.getSongById(songId)
            song?.let {
                withContext(Dispatchers.Main) {
                    // 現在のコンテキストに応じて適切なメソッドを呼び出す
                    when {
                        currentPlaylistId != null -> {
                            val songs = playlistRepository.getSongsInPlaylist(currentPlaylistId!!).first()
                            playerManager.playSongFromPlaylist(currentPlaylistId!!, songs, it.filePath, it.id)
                        }
                        currentAlbum != null -> {
                            val songs = musicRepository.getSongsByAlbum(currentAlbum!!).first()
                            playerManager.playSongFromAlbum(currentAlbum!!, songs, it.filePath, it.id)
                        }
                        currentArtist != null -> {
                            val songs = musicRepository.getSongsByArtist(currentArtist!!).first()
                            playerManager.playSongFromArtist(currentArtist!!, songs, it.filePath, it.id)
                        }
                        currentFolderPath != null -> {
                            val songs = musicRepository.getSongsByFolder(currentFolderPath!!).first()
                            playerManager.playSongFromFolder(currentFolderPath!!, songs, it.filePath, it.id)
                        }
                        else -> {
                            // 通常モード: コンテキストをクリア
                            playerManager.clearContextMode()
                            playerManager.playSong(it.filePath, it.id)
                        }
                    }
                }
            }
        }
    }
    
    if (!hasPermissions) {
        PermissionScreen(
            onPermissionsGranted = { hasPermissions = true }
        )
    } else when {
        currentSongDetailId != null -> {
            SongDetailScreen(
                songId = currentSongDetailId!!,
                onBack = { currentSongDetailId = null }
            )
        }
        showWidgetSettings -> {
            WidgetSettingsScreen(
                onBack = { showWidgetSettings = false }
            )
        }
        showSettings -> {
            SettingsScreen(
                onBack = { showSettings = false },
                onThemeChanged = onThemeChanged,
                onWidgetSettingsClick = { showWidgetSettings = true }
            )
        }
        currentSongId != null -> {
            PlayerScreen(
                songId = currentSongId!!,
                onBack = { currentSongId = null }
            )
        }
        currentAlbum != null -> {
            AlbumDetailScreen(
                albumName = currentAlbum!!,
                onBack = { currentAlbum = null },
                onSongSelected = onSongClick,
                onAlbumSelected = { album -> currentAlbum = album },
                onArtistSelected = { artist -> currentArtist = artist },
                onFolderSelected = { folderPath -> currentFolderPath = folderPath },
                onPlaylistSelected = { playlistId -> currentPlaylistId = playlistId },
                onSongDetail = { songId -> currentSongDetailId = songId }
            )
        }
        currentArtist != null -> {
            ArtistDetailScreen(
                artistName = currentArtist!!,
                onBack = { currentArtist = null },
                onSongSelected = onSongClick,
                onAlbumSelected = { album -> currentAlbum = album },
                onArtistSelected = { artist -> currentArtist = artist },
                onFolderSelected = { folderPath -> currentFolderPath = folderPath },
                onPlaylistSelected = { playlistId -> currentPlaylistId = playlistId }
            )
        }
        currentFolderPath != null -> {
            FolderDetailScreen(
                folderPath = currentFolderPath!!,
                onBack = { currentFolderPath = null },
                onSongSelected = onSongClick,
                onAlbumSelected = { album -> currentAlbum = album },
                onArtistSelected = { artist -> currentArtist = artist },
                onFolderSelected = { folderPath -> currentFolderPath = folderPath },
                onPlaylistSelected = { playlistId -> currentPlaylistId = playlistId }
            )
        }
        currentPlaylistId != null -> {
            PlaylistDetailScreen(
                playlistId = currentPlaylistId!!,
                onBack = { currentPlaylistId = null },
                onSongSelected = onSongClick,
                onAlbumSelected = { album -> currentAlbum = album },
                onArtistSelected = { artist -> currentArtist = artist },
                onFolderSelected = { folderPath -> currentFolderPath = folderPath },
                onPlaylistSelected = { playlistId -> currentPlaylistId = playlistId }
            )
        }
        else -> {
            HomeScreen(
                onSongSelected = onSongClick,
                onAlbumSelected = { album -> currentAlbum = album },
                onArtistSelected = { artist -> currentArtist = artist },
                onFolderSelected = { folderPath -> currentFolderPath = folderPath },
                onPlaylistSelected = { playlistId -> currentPlaylistId = playlistId },
                onSettingsClick = { showSettings = true },
                onSongDetail = { songId -> currentSongDetailId = songId }
            )
        }
    }
}

