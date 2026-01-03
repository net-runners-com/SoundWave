package com.example.soundwave.player

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log

data class EqualizerSettings(
    val enabled: Boolean = false,
    val bands: Map<Int, Short> = emptyMap() // バンド番号 -> ゲイン値
)

data class AudioEffectSettings(
    val equalizer: EqualizerSettings = EqualizerSettings(),
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Short = 0, // 0-1000
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Short = 0, // 0-1000
    val playbackSpeed: Float = 1.0f, // 0.5x - 2.0x
    val pitch: Float = 1.0f // 0.5 - 2.0 (セミトーン単位で調整)
)

class AudioEffectManager(private val context: Context) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    
    private var audioSessionId: Int = AudioEffect.ERROR_BAD_VALUE
    
    fun attachToAudioSession(sessionId: Int) {
        if (sessionId == AudioEffect.ERROR_BAD_VALUE) {
            Log.w("AudioEffectManager", "Invalid audio session ID")
            return
        }
        
        release()
        audioSessionId = sessionId
        
        try {
            // イコライザー
            equalizer = Equalizer(Int.MAX_VALUE, sessionId).apply {
                enabled = false
            }
            Log.d("AudioEffectManager", "Equalizer created: ${equalizer?.numberOfBands} bands")
        } catch (e: Exception) {
            Log.e("AudioEffectManager", "Failed to create Equalizer", e)
        }
        
        try {
            // ベースブースト
            bassBoost = BassBoost(Int.MAX_VALUE, sessionId).apply {
                enabled = false
            }
            Log.d("AudioEffectManager", "BassBoost created")
        } catch (e: Exception) {
            Log.e("AudioEffectManager", "Failed to create BassBoost", e)
        }
        
        try {
            // バーチャライザー
            virtualizer = Virtualizer(Int.MAX_VALUE, sessionId).apply {
                enabled = false
            }
            Log.d("AudioEffectManager", "Virtualizer created")
        } catch (e: Exception) {
            Log.e("AudioEffectManager", "Failed to create Virtualizer", e)
        }
    }
    
    fun applySettings(settings: AudioEffectSettings) {
        // イコライザー設定
        equalizer?.let { eq ->
            try {
                eq.enabled = settings.equalizer.enabled
                if (settings.equalizer.enabled) {
                    // バンド設定を適用
                    val numBands = eq.numberOfBands
                    settings.equalizer.bands.forEach { (band, gain) ->
                        val bandIndex = band.coerceIn(0, numBands - 1)
                        val levelRange = eq.bandLevelRange
                        val minLevel = levelRange[0]
                        val maxLevel = levelRange[1]
                        val clampedGain = gain.coerceIn(minLevel, maxLevel)
                        eq.setBandLevel(bandIndex.toShort(), clampedGain)
                    }
                } else {
                    // 無効化時は何もしない
                }
            } catch (e: Exception) {
                Log.e("AudioEffectManager", "Failed to apply equalizer settings", e)
            }
        }
        
        // ベースブースト設定
        bassBoost?.let { bb ->
            try {
                bb.enabled = settings.bassBoostEnabled
                if (settings.bassBoostEnabled) {
                    bb.setStrength(settings.bassBoostStrength)
                } else {
                    // 無効化時は何もしない
                }
            } catch (e: Exception) {
                Log.e("AudioEffectManager", "Failed to apply bass boost settings", e)
            }
        }
        
        // バーチャライザー設定
        virtualizer?.let { v ->
            try {
                v.enabled = settings.virtualizerEnabled
                if (settings.virtualizerEnabled) {
                    v.setStrength(settings.virtualizerStrength)
                } else {
                    // 無効化時は何もしない
                }
            } catch (e: Exception) {
                Log.e("AudioEffectManager", "Failed to apply virtualizer settings", e)
            }
        }
    }
    
    fun getEqualizer(): Equalizer? = equalizer
    fun getBassBoost(): BassBoost? = bassBoost
    fun getVirtualizer(): Virtualizer? = virtualizer
    
    fun release() {
        try {
            equalizer?.release()
            equalizer = null
        } catch (e: Exception) {
            Log.e("AudioEffectManager", "Failed to release Equalizer", e)
        }
        
        try {
            bassBoost?.release()
            bassBoost = null
        } catch (e: Exception) {
            Log.e("AudioEffectManager", "Failed to release BassBoost", e)
        }
        
        try {
            virtualizer?.release()
            virtualizer = null
        } catch (e: Exception) {
            Log.e("AudioEffectManager", "Failed to release Virtualizer", e)
        }
        
        audioSessionId = AudioEffect.ERROR_BAD_VALUE
    }
}

