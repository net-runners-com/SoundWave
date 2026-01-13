package com.example.soundwave.ui.components

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class TabItem(
    val icon: ImageVector,
    val titleResId: Int
) {
    SONGS(Icons.Default.MusicNote, android.R.string.unknownName), // Will be set via getTitle
    ALBUMS(Icons.Default.Album, android.R.string.unknownName),
    ARTISTS(Icons.Default.Person, android.R.string.unknownName),
    FOLDERS(Icons.Default.Folder, android.R.string.unknownName),
    PLAYLISTS(Icons.Default.PlaylistPlay, android.R.string.unknownName),
    YOUTUBE(Icons.Default.VideoLibrary, android.R.string.unknownName),
    MAP(Icons.Default.Map, android.R.string.unknownName);
    
    fun getTitle(context: Context): String {
        return when (this) {
            SONGS -> context.getString(com.example.soundwave.R.string.tab_songs)
            ALBUMS -> context.getString(com.example.soundwave.R.string.tab_albums)
            ARTISTS -> context.getString(com.example.soundwave.R.string.tab_artists)
            FOLDERS -> context.getString(com.example.soundwave.R.string.tab_folders)
            PLAYLISTS -> context.getString(com.example.soundwave.R.string.tab_playlists)
            YOUTUBE -> context.getString(com.example.soundwave.R.string.tab_youtube)
            MAP -> context.getString(com.example.soundwave.R.string.tab_map)
        }
    }
}




