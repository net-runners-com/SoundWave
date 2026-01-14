package com.example.soundwave.ui.download

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.example.soundwave.data.repository.YouTubeRepository
import com.example.soundwave.data.AppDatabaseModule
import com.example.soundwave.ui.download.DownloadProgressManager
import android.media.MediaScannerConnection
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadHistoryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { YouTubeRepository(context) }
    val musicRepository = remember { AppDatabaseModule.getMusicRepository(context) }
    
    // DownloadHistoryManagerを初期化
    LaunchedEffect(Unit) {
        DownloadHistoryManager.initialize(context)
    }
    
    val downloadHistory by DownloadHistoryManager.downloadHistory.collectAsState()
    val activeDownloads by DownloadHistoryManager.activeDownloads.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ダウンロード履歴") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    if (downloadHistory.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    DownloadHistoryManager.clearHistory()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "履歴をクリア")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (downloadHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "ダウンロード履歴がありません",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(downloadHistory, key = { it.id }) { item ->
                    DownloadHistoryItem(
                        item = item,
                        isActive = activeDownloads.containsKey(item.id) && activeDownloads[item.id]?.isActive == true,
                        onCancel = {
                            scope.launch(Dispatchers.IO) {
                                // アクティブなダウンロードの場合はキャンセル、そうでない場合は履歴から削除
                                if (activeDownloads.containsKey(item.id) && activeDownloads[item.id]?.isActive == true) {
                                    DownloadHistoryManager.cancelDownload(item.id)
                                } else {
                                    DownloadHistoryManager.removeDownload(item.id)
                                }
                            }
                        },
                        onPause = {
                            scope.launch(Dispatchers.IO) {
                                DownloadHistoryManager.pauseDownload(item.id)
                            }
                        },
                        onResume = {
                            scope.launch {
                                try {
                                    // 新しいダウンロードIDを生成
                                    val newDownloadId = DownloadHistoryManager.resumeDownload(
                                        item.id,
                                        item.videoId,
                                        item.format,
                                        item.quality,
                                        item.videoTitle
                                    )
                                    
                                    // ダウンロード開始
                                    DownloadProgressManager.startDownload()
                                    
                                    // ダウンロードJobを作成
                                    var downloadJob: kotlinx.coroutines.Job? = null
                                    downloadJob = scope.launch(Dispatchers.IO) {
                                        try {
                                            val filePath = repository.downloadVideo(
                                                videoId = item.videoId,
                                                format = item.format,
                                                quality = item.quality,
                                                onProgress = { progress, eta ->
                                                    // 進捗を更新（コルーチンスコープ内で実行）
                                                    scope.launch(Dispatchers.IO) {
                                                        DownloadHistoryManager.updateDownload(
                                                            newDownloadId,
                                                            DownloadStatus.DOWNLOADING,
                                                            progress
                                                        )
                                                    }
                                                    android.util.Log.d("DownloadHistory", "Progress: $progress%, ETA: $eta seconds")
                                                }
                                            )
                                            
                                            if (filePath != null) {
                                                val fileName = File(filePath).name
                                                // ダウンロード完了
                                                DownloadHistoryManager.updateDownload(
                                                    newDownloadId,
                                                    DownloadStatus.COMPLETED,
                                                    100f,
                                                    filePath
                                                )
                                                DownloadHistoryManager.removeActiveDownload(newDownloadId)
                                                DownloadProgressManager.finishDownload("ダウンロード完了: $fileName")
                                                // メディアスキャンにファイルを登録
                                                MediaScannerConnection.scanFile(
                                                    context,
                                                    arrayOf(filePath),
                                                    null
                                                ) { path, uri ->
                                                    android.util.Log.d("DownloadHistory", "Media scan completed: $path -> $uri")
                                                    scope.launch(Dispatchers.IO) {
                                                        kotlinx.coroutines.delay(2000)
                                                        musicRepository.scanMusicFiles()
                                                    }
                                                }
                                            } else {
                                                // ダウンロード失敗
                                                DownloadHistoryManager.updateDownload(
                                                    newDownloadId,
                                                    DownloadStatus.FAILED,
                                                    0f
                                                )
                                                DownloadHistoryManager.removeActiveDownload(newDownloadId)
                                                DownloadProgressManager.finishDownload("ダウンロードに失敗しました。")
                                            }
                                        } catch (e: Exception) {
                                            DownloadHistoryManager.updateDownload(
                                                newDownloadId,
                                                DownloadStatus.FAILED,
                                                0f
                                            )
                                            DownloadHistoryManager.removeActiveDownload(newDownloadId)
                                            DownloadProgressManager.cancelDownload()
                                            android.util.Log.e("DownloadHistory", "Download error", e)
                                        }
                                    }
                                    // Jobを登録
                                    downloadJob?.let { job ->
                                        DownloadHistoryManager.setActiveDownload(newDownloadId, job)
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("DownloadHistory", "Failed to resume download", e)
                                    DownloadProgressManager.cancelDownload()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadHistoryItem(
    item: DownloadItem,
    isActive: Boolean,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.videoTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${item.format} - ${item.quality}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // ステータス表示
                when (item.status) {
                    DownloadStatus.DOWNLOADING -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isActive) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    progress = item.progress / 100f
                                )
                            } else {
                                Text(
                                    text = "${item.progress.toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = onPause
                            ) {
                                Icon(
                                    Icons.Default.Pause,
                                    contentDescription = "一時停止",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = onCancel
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "中断",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "一時停止",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = onResume
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "再開",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = onCancel
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "中断",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    DownloadStatus.PENDING -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "待機中",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = onPause
                            ) {
                                Icon(
                                    Icons.Default.Pause,
                                    contentDescription = "一時停止",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = onCancel
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "中断",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "完了",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = onCancel
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "削除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    DownloadStatus.FAILED -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "失敗",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = onCancel
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "削除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    DownloadStatus.CANCELLED -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = "キャンセル",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = onCancel
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "削除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
            
            // 進捗バー（ダウンロード中の場合）
            if (item.status == DownloadStatus.DOWNLOADING && item.progress > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = item.progress / 100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 日時表示
            Text(
                text = dateFormat.format(Date(item.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
