package com.example.soundwave.ui.song

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.soundwave.data.AppDatabaseModule
import com.example.soundwave.util.TimeFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailBottomSheet(
    songId: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val musicRepository = remember { AppDatabaseModule.getMusicRepository(context) }
    
    var song by remember { mutableStateOf<com.example.soundwave.data.database.SongEntity?>(null) }
    
    // 曲情報を読み込む
    LaunchedEffect(songId) {
        withContext(Dispatchers.IO) {
            song = musicRepository.getSongById(songId)
        }
    }
    
    song?.let { currentSong ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 曲情報
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailRow("タイトル", currentSong.title)
                    Divider()
                    DetailRow("アーティスト", currentSong.artist)
                    Divider()
                    DetailRow("アルバム", currentSong.album)
                    Divider()
                    
                    // ファイル名
                    val fileName = File(currentSong.filePath).name
                    DetailRow("ファイル名", fileName)
                    Divider()
                    
                    // ファイルサイズ（MB）
                    val fileSize = try {
                        val file = File(currentSong.filePath)
                        if (file.exists()) {
                            val sizeInBytes = file.length()
                            String.format("%.2f", sizeInBytes / (1024.0 * 1024.0))
                        } else {
                            "不明"
                        }
                    } catch (e: Exception) {
                        "不明"
                    }
                    DetailRow("サイズ", "$fileSize MB")
                    Divider()
                    
                    // 更新日
                    val updateDate = currentSong.fileLastModified?.let {
                        val dateFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault())
                        dateFormat.format(Date(it))
                    } ?: "不明"
                    DetailRow("更新日", updateDate)
                    Divider()
                    
                    // 曲の長さ
                    val duration = TimeFormatter.format(currentSong.duration)
                    DetailRow("長さ", duration)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

