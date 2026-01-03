package com.example.soundwave.player

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val currentSongId: Long? = null
)



