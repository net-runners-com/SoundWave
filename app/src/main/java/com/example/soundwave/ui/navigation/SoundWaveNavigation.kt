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
import com.example.soundwave.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SoundWaveNavigation(
    onThemeChanged: (AppTheme) -> Unit = {}
) {
    val context = LocalContext.current
    val playerManager = remember { PlayerManager.getInstance(context) }
    val musicRepository = remember { AppDatabaseModule.getMusicRepository(context) }
    
    var hasPermissions by remember { mutableStateOf(false) }
    var currentSongId by remember { mutableStateOf<Long?>(null) }
    var currentAlbum by remember { mutableStateOf<String?>(null) }
    var currentArtist by remember { mutableStateOf<String?>(null) }
    var currentFolderPath by remember { mutableStateOf<String?>(null) }
    var currentPlaylistId by remember { mutableStateOf<Long?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    
    // 曲をクリックしたときの処理（再生のみ、画面遷移しない）
    val onSongClick: (Long) -> Unit = { songId ->
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val song = musicRepository.getSongById(songId)
            song?.let {
                withContext(Dispatchers.Main) {
                    playerManager.playSong(it.filePath, it.id)
                }
            }
        }
    }
    
    if (!hasPermissions) {
        PermissionScreen(
            onPermissionsGranted = { hasPermissions = true }
        )
    } else when {
        showSettings -> {
            SettingsScreen(
                onBack = { showSettings = false },
                onThemeChanged = onThemeChanged
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
                onPlaylistSelected = { playlistId -> currentPlaylistId = playlistId }
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
                onSettingsClick = { showSettings = true }
            )
        }
    }
}

