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
    BEST, HD_1080, HD_720, SD_480, SD_360
}

@Composable
fun YouTubeDownloadDialog(
    videoTitle: String,
    onDismiss: () -> Unit,
    onDownload: (format: DownloadFormat, quality: DownloadQuality) -> Unit
) {
    var selectedFormat by remember { mutableStateOf(DownloadFormat.MP4) }
    var selectedQuality by remember { mutableStateOf(DownloadQuality.BEST) }
    
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
                
                // 形式選択
                Text(
                    text = "形式",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DownloadFormat.values().forEach { format ->
                        FilterChip(
                            selected = selectedFormat == format,
                            onClick = { selectedFormat = format },
                            label = { Text(format.name) }
                        )
                    }
                }
                
                // 解像度選択
                Text(
                    text = "解像度",
                    style = MaterialTheme.typography.labelLarge
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DownloadQuality.values().forEach { quality ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedQuality == quality,
                                onClick = { selectedQuality = quality }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (quality) {
                                    DownloadQuality.BEST -> "最高品質"
                                    DownloadQuality.HD_1080 -> "1080p (HD)"
                                    DownloadQuality.HD_720 -> "720p (HD)"
                                    DownloadQuality.SD_480 -> "480p"
                                    DownloadQuality.SD_360 -> "360p"
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
                    onDownload(selectedFormat, selectedQuality)
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

