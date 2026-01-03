package com.example.soundwave.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.soundwave.player.AudioEffectSettings
import com.example.soundwave.player.AudioEffectSettingsManager
import com.example.soundwave.player.PlayerManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEffectDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val playerManager = remember { PlayerManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    // イコライザーのバンド情報を取得
    val equalizer = remember {
        playerManager.getAudioEffectManager()?.getEqualizer()
    }
    
    // ボトムシートの状態（半分まで展開可能）
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    
    // 現在の設定を読み込む
    var currentSettings by remember {
        mutableStateOf(AudioEffectSettingsManager.loadSettings(context))
    }
    
    ModalBottomSheet(
        onDismissRequest = {
            scope.launch {
                bottomSheetState.hide()
            }.invokeOnCompletion {
                onDismiss()
            }
        },
        sheetState = bottomSheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
            }
        }
    ) {
        val configuration = LocalConfiguration.current
        val screenHeight = with(LocalDensity.current) {
            configuration.screenHeightDp.dp.toPx()
        }
        val maxHeight = with(LocalDensity.current) {
            (screenHeight / 2).toDp()
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // タイトル
            Text(
                text = "エフェクト設定",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // イコライザー
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "イコライザー",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Switch(
                                checked = currentSettings.equalizer.enabled,
                                onCheckedChange = { enabled ->
                                    currentSettings = currentSettings.copy(
                                        equalizer = currentSettings.equalizer.copy(enabled = enabled)
                                    )
                                    AudioEffectSettingsManager.saveSettings(context, currentSettings)
                                    playerManager.applyAudioEffectSettings(currentSettings)
                                }
                            )
                        }
                        
                        if (currentSettings.equalizer.enabled && equalizer != null) {
                            val numBands = equalizer.numberOfBands
                            val levelRange = equalizer.bandLevelRange
                            val minLevel = levelRange[0]
                            val maxLevel = levelRange[1]
                            
                            // 各バンドのスライダーを表示
                            (0 until numBands).forEach { bandIndex ->
                                val centerFreq = equalizer.getCenterFreq(bandIndex.toShort()) / 1000 // Hz to kHz
                                val currentGain = currentSettings.equalizer.bands[bandIndex]?.toFloat() ?: 0f
                                
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${centerFreq}Hz",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "${currentGain.toInt()}dB",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Slider(
                                        value = currentGain,
                                        onValueChange = { value ->
                                            val gain = value.toInt().coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
                                            val updatedBands = currentSettings.equalizer.bands.toMutableMap()
                                            updatedBands[bandIndex] = gain
                                            currentSettings = currentSettings.copy(
                                                equalizer = currentSettings.equalizer.copy(
                                                    bands = updatedBands
                                                )
                                            )
                                            AudioEffectSettingsManager.saveSettings(context, currentSettings)
                                            playerManager.applyAudioEffectSettings(currentSettings)
                                        },
                                        valueRange = minLevel.toFloat()..maxLevel.toFloat(),
                                        steps = ((maxLevel.toInt() - minLevel.toInt()) / 100) // 100mB刻み
                                    )
                                }
                            }
                        }
                    }
                }
                
                // ベースブースト
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ベースブースト",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Switch(
                                checked = currentSettings.bassBoostEnabled,
                                onCheckedChange = { enabled ->
                                    currentSettings = currentSettings.copy(
                                        bassBoostEnabled = enabled
                                    )
                                    AudioEffectSettingsManager.saveSettings(context, currentSettings)
                                    playerManager.applyAudioEffectSettings(currentSettings)
                                }
                            )
                        }
                        
                        if (currentSettings.bassBoostEnabled) {
                            Text(
                                text = "強度: ${currentSettings.bassBoostStrength / 10}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = currentSettings.bassBoostStrength.toFloat(),
                                onValueChange = { value ->
                                    val strength = value.toInt().coerceIn(0, 1000).toShort()
                                    currentSettings = currentSettings.copy(
                                        bassBoostStrength = strength
                                    )
                                    AudioEffectSettingsManager.saveSettings(context, currentSettings)
                                    playerManager.applyAudioEffectSettings(currentSettings)
                                },
                                valueRange = 0f..1000f,
                                steps = 19 // 5%刻み
                            )
                        }
                    }
                }
                
                // バーチャライザー
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "バーチャライザー",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Switch(
                                checked = currentSettings.virtualizerEnabled,
                                onCheckedChange = { enabled ->
                                    currentSettings = currentSettings.copy(
                                        virtualizerEnabled = enabled
                                    )
                                    AudioEffectSettingsManager.saveSettings(context, currentSettings)
                                    playerManager.applyAudioEffectSettings(currentSettings)
                                }
                            )
                        }
                        
                        if (currentSettings.virtualizerEnabled) {
                            Text(
                                text = "強度: ${currentSettings.virtualizerStrength / 10}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = currentSettings.virtualizerStrength.toFloat(),
                                onValueChange = { value ->
                                    val strength = value.toInt().coerceIn(0, 1000).toShort()
                                    currentSettings = currentSettings.copy(
                                        virtualizerStrength = strength
                                    )
                                    AudioEffectSettingsManager.saveSettings(context, currentSettings)
                                    playerManager.applyAudioEffectSettings(currentSettings)
                                },
                                valueRange = 0f..1000f,
                                steps = 19 // 5%刻み
                            )
                        }
                    }
                }
                
                // 再生速度
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "再生速度",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${String.format("%.2f", currentSettings.playbackSpeed)}x",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = currentSettings.playbackSpeed,
                            onValueChange = { value ->
                                val speed = value.coerceIn(0.5f, 2.0f)
                                currentSettings = currentSettings.copy(
                                    playbackSpeed = speed
                                )
                                AudioEffectSettingsManager.saveSettings(context, currentSettings)
                                playerManager.applyAudioEffectSettings(currentSettings)
                            },
                            valueRange = 0.5f..2.0f,
                            steps = 14 // 0.1刻み
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "0.5x",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "1.0x",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "2.0x",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                
                // ピッチ
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ピッチ",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${String.format("%.2f", currentSettings.pitch)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = currentSettings.pitch,
                            onValueChange = { value ->
                                val pitch = value.coerceIn(0.5f, 2.0f)
                                currentSettings = currentSettings.copy(
                                    pitch = pitch
                                )
                                AudioEffectSettingsManager.saveSettings(context, currentSettings)
                                playerManager.applyAudioEffectSettings(currentSettings)
                            },
                            valueRange = 0.5f..2.0f,
                            steps = 14 // 0.1刻み
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "0.5",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "1.0",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "2.0",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}


