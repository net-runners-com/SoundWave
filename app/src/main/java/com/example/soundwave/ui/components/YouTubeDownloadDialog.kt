@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.soundwave.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class DownloadFormat {
    MP4, MP3, WEBM
}

enum class DownloadQuality {
    BEST, HD_1080, HD_720, SD_480, SD_360,
    // MP3用ビットレート
    MP3_96, MP3_128, MP3_256, MP3_320
}

enum class AudioBitrate {
    KBPS_96,   // 低品質
    KBPS_128,  // 標準
    KBPS_256,  // 高音質
    KBPS_320   // 最高品質
}

@Composable
fun YouTubeDownloadDialog(
    videoTitle: String,
    onDismiss: () -> Unit,
    onDownload: (format: DownloadFormat, quality: DownloadQuality) -> Unit
) {
    // MP3に固定
    val selectedFormat = DownloadFormat.MP3
    var selectedBitrate by remember { mutableStateOf(AudioBitrate.KBPS_320) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("ダウンロード設定")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = videoTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // ビットレート選択
                Text(
                    text = "ビットレート",
                    style = MaterialTheme.typography.labelLarge
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AudioBitrate.values().reversed().forEach { bitrate ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedBitrate == bitrate,
                                onClick = { selectedBitrate = bitrate }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (bitrate) {
                                    AudioBitrate.KBPS_96 -> "96kbps（低品質）"
                                    AudioBitrate.KBPS_128 -> "128kbps（標準）"
                                    AudioBitrate.KBPS_256 -> "256kbps（高音質）"
                                    AudioBitrate.KBPS_320 -> "320kbps（最高品質）"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // ビットレートをDownloadQualityにマッピング
                    val quality = when (selectedBitrate) {
                        AudioBitrate.KBPS_96 -> DownloadQuality.MP3_96
                        AudioBitrate.KBPS_128 -> DownloadQuality.MP3_128
                        AudioBitrate.KBPS_256 -> DownloadQuality.MP3_256
                        AudioBitrate.KBPS_320 -> DownloadQuality.MP3_320
                    }
                    onDownload(selectedFormat, quality)
                    onDismiss()
                }
            ) {
                Text("ダウンロード")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

