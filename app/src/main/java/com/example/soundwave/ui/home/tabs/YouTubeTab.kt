@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.example.soundwave.ui.home.tabs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.soundwave.data.repository.YouTubeRepository
import com.example.soundwave.data.repository.YouTubeVideo
import com.example.soundwave.ui.components.EmptyState
import com.example.soundwave.ui.components.YouTubeDownloadDialog
import com.example.soundwave.ui.components.DownloadFormat
import com.example.soundwave.ui.components.DownloadQuality
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File

@Composable
fun YouTubeTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val viewModel: YouTubeViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return YouTubeViewModel(context.applicationContext as android.app.Application) as T
            }
        }
    )
    val repository = remember { YouTubeRepository(context) }
    
    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchResults by viewModel.searchResults.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    // YouTube動画を開く
    fun openYouTubeVideo(video: YouTubeVideo) {
        val videoUrl = repository.getVideoUrl(video.id)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
        intent.setPackage("com.google.android.youtube") // YouTubeアプリで開く
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // YouTubeアプリがない場合はブラウザで開く
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
            context.startActivity(browserIntent)
        }
    }
    
    // リンクをコピー
    fun copyLink(videoUrl: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("YouTube URL", videoUrl)
        clipboard.setPrimaryClip(clip)
        scope.launch {
            snackbarHostState.showSnackbar("リンクをコピーしました")
        }
    }
    
    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
    val layoutDirection = LocalLayoutDirection.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = paddingValues.calculateStartPadding(layoutDirection),
                end = paddingValues.calculateEndPadding(layoutDirection),
                bottom = paddingValues.calculateBottomPadding()
            )
    ) {
        // 検索バー
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("YouTube検索") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { 
                        searchQuery = ""
                        viewModel.clearResults()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "クリア")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (searchQuery.isNotBlank() && !isLoading) {
                        viewModel.searchVideos(searchQuery)
                        keyboardController?.hide()
                    }
                }
            )
        )
        
        // 検索履歴または検索結果
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null && searchResults.isEmpty() -> {
                EmptyState(
                    message = errorMessage ?: "エラーが発生しました",
                    actionLabel = "再試行",
                    onAction = { viewModel.searchVideos(searchQuery) }
                )
            }
            searchResults.isNotEmpty() -> {
                // 検索結果を表示
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults, key = { it.id }) { video ->
                        YouTubeVideoItem(
                            video = video,
                            repository = repository,
                            onClick = { openYouTubeVideo(video) },
                            onCopyLink = { copyLink(it) },
                            onOpenYouTube = { openYouTubeVideo(video) },
                            onDownload = { format, quality ->
                                scope.launch {
                                    try {
                                        snackbarHostState.showSnackbar("ダウンロード開始中...")
                                        val filePath = withContext(Dispatchers.IO) {
                                            repository.downloadVideo(
                                                videoId = video.id,
                                                format = format.name,
                                                quality = quality.name,
                                                onProgress = { progress, eta ->
                                                    // 進捗更新（必要に応じて）
                                                    android.util.Log.d("YouTubeDownload", "Progress: $progress%, ETA: $eta seconds")
                                                }
                                            )
                                        }
                                        if (filePath != null) {
                                            val fileName = File(filePath).name
                                            snackbarHostState.showSnackbar("ダウンロード完了: $fileName")
                                        } else {
                                            snackbarHostState.showSnackbar("ダウンロードに失敗しました。YoutubeDLの初期化に失敗している可能性があります。")
                                        }
                                    } catch (e: com.yausername.youtubedl_android.YoutubeDLException) {
                                        android.util.Log.e("YouTubeTab", "YoutubeDL error", e)
                                        val errorMsg = when {
                                            e.message?.contains("failed to initialize") == true -> 
                                                "YoutubeDLの初期化に失敗しました。アプリを再起動してください。"
                                            e.message?.contains("libpython") == true -> 
                                                "必要なライブラリが見つかりません。アプリを再インストールしてください。"
                                            else -> "ダウンロードエラー: ${e.message ?: "不明なエラー"}"
                                        }
                                        snackbarHostState.showSnackbar(errorMsg)
                                    } catch (e: Exception) {
                                        android.util.Log.e("YouTubeTab", "Download error", e)
                                        snackbarHostState.showSnackbar("エラー: ${e.message ?: "不明なエラー"}")
                                    }
                                }
                            }
                        )
                    }
                }
            }
            else -> {
                // 検索履歴を表示
                if (searchHistory.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "最近の検索",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            TextButton(onClick = { viewModel.clearSearchHistory() }) {
                                Text("すべて削除")
                            }
                        }
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(searchHistory, key = { it }) { historyItem ->
                                SearchHistoryItem(
                                    query = historyItem,
                                    onClick = {
                                        searchQuery = historyItem
                                        viewModel.searchVideos(historyItem)
                                    },
                                    onDelete = {
                                        viewModel.removeSearchQuery(historyItem)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    EmptyState(message = "検索キーワードを入力して検索してください")
                }
            }
        }
    }
        
    }
}

@Composable
fun SearchHistoryItem(
    query: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp)
            )
            
            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "削除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun YouTubeVideoItem(
    video: YouTubeVideo,
    repository: YouTubeRepository,
    onClick: () -> Unit,
    onCopyLink: (String) -> Unit,
    onOpenYouTube: () -> Unit,
    onDownload: (DownloadFormat, DownloadQuality) -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    
    if (showDownloadDialog) {
        YouTubeDownloadDialog(
            videoTitle = video.title,
            onDismiss = { showDownloadDialog = false },
            onDownload = { format, quality ->
                onDownload(format, quality)
            }
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // サムネイル
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(video.thumbnailUrl)
                    .crossfade(true)
                    .placeholder(android.R.drawable.ic_media_play)
                    .error(android.R.drawable.ic_media_play)
                    .build(),
                contentDescription = video.title,
                modifier = Modifier
                    .size(120.dp, 90.dp)
                    .padding(end = 12.dp)
            )
            
            // 動画情報
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (video.duration != null) {
                    Text(
                        text = video.duration,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // リンクコピーボタン
            IconButton(
                onClick = {
                    val videoUrl = repository.getVideoUrl(video.id)
                    onCopyLink(videoUrl)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "リンクをコピー",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // ケバブメニュー
            Box {
                IconButton(
                    onClick = { showMenu = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "メニュー",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("開く") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onOpenYouTube()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("ダウンロード") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            showDownloadDialog = true
                        }
                    )
                }
            }
        }
    }
}
