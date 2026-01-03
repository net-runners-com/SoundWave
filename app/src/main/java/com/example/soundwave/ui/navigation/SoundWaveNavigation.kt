package com.example.soundwave.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.soundwave.ui.permission.PermissionScreen
import com.example.soundwave.ui.home.HomeScreen
import com.example.soundwave.ui.player.PlayerScreen

@Composable
fun SoundWaveNavigation() {
    var hasPermissions by remember { mutableStateOf(false) }
    var currentSongId by remember { mutableStateOf<Long?>(null) }
    var currentAlbum by remember { mutableStateOf<String?>(null) }
    var currentArtist by remember { mutableStateOf<String?>(null) }
    var currentPlaylistId by remember { mutableStateOf<Long?>(null) }
    
    if (!hasPermissions) {
        PermissionScreen(
            onPermissionsGranted = { hasPermissions = true }
        )
    } else when {
        currentSongId != null -> {
            PlayerScreen(
                songId = currentSongId!!,
                onBack = { currentSongId = null }
            )
        }
        currentAlbum != null -> {
            // TODO: アルバム詳細画面
            HomeScreen(
                onSongSelected = { songId -> currentSongId = songId },
                onAlbumSelected = { album -> currentAlbum = album },
                onArtistSelected = { artist -> currentArtist = artist },
                onPlaylistSelected = { playlistId -> currentPlaylistId = playlistId }
            )
        }
        currentArtist != null -> {
            // TODO: アーティスト詳細画面
            HomeScreen(
                onSongSelected = { songId -> currentSongId = songId },
                onAlbumSelected = { album -> currentAlbum = album },
                onArtistSelected = { artist -> currentArtist = artist },
                onPlaylistSelected = { playlistId -> currentPlaylistId = playlistId }
            )
        }
        currentPlaylistId != null -> {
            // TODO: プレイリスト詳細画面
            HomeScreen(
                onSongSelected = { songId -> currentSongId = songId },
                onAlbumSelected = { album -> currentAlbum = album },
                onArtistSelected = { artist -> currentArtist = artist },
                onPlaylistSelected = { playlistId -> currentPlaylistId = playlistId }
            )
        }
        else -> {
            HomeScreen(
                onSongSelected = { songId -> currentSongId = songId },
                onAlbumSelected = { album -> currentAlbum = album },
                onArtistSelected = { artist -> currentArtist = artist },
                onPlaylistSelected = { playlistId -> currentPlaylistId = playlistId }
            )
        }
    }
}

