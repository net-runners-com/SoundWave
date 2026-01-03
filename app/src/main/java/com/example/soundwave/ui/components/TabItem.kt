package com.example.soundwave.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class TabItem(
    val title: String,
    val icon: ImageVector
) {
    SONGS("曲", Icons.Default.MusicNote),
    ALBUMS("アルバム", Icons.Default.Album),
    ARTISTS("アーティスト", Icons.Default.Person),
    FOLDERS("フォルダ", Icons.Default.Folder),
    PLAYLISTS("プレイリスト", Icons.Default.PlaylistPlay),
    YOUTUBE("YouTube", Icons.Default.VideoLibrary)
}



