package com.example.soundwave.util

object Constants {
    const val NOTIFICATION_CHANNEL_ID = "music_player_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Music Player"
    const val NOTIFICATION_ID = 1
    const val ACTION_PLAY_PAUSE = "com.example.soundwave.PLAY_PAUSE"
    const val ACTION_PREVIOUS = "com.example.soundwave.PREVIOUS"
    const val ACTION_NEXT = "com.example.soundwave.NEXT"
    
    const val POSITION_UPDATE_INTERVAL_MS = 100L
    
    // ウィジェット用のSharedPreferencesキー
    const val PREFS_WIDGET = "widget_prefs"
    const val KEY_WIDGET_SONG_TITLE = "widget_song_title"
    const val KEY_WIDGET_SONG_ARTIST = "widget_song_artist"
    const val KEY_WIDGET_ALBUM_ART_PATH = "widget_album_art_path"
    const val KEY_WIDGET_IS_PLAYING = "widget_is_playing"
}




