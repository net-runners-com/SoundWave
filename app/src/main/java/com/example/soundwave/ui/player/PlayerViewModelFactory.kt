package com.example.soundwave.ui.player

import android.app.Application
import androidx.lifecycle.ViewModelProvider

class PlayerViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
            return PlayerViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}



