@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.soundwave.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.soundwave.data.repository.LRCLicSearchResult
import com.example.soundwave.data.repository.LyricLine
import com.example.soundwave.player.PlayerManager
import com.example.soundwave.util.Constants
import com.example.soundwave.util.TimeFormatter
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    songId: Long,
    onBack: () -> Unit,
    onEditLyrics: () -> Unit
) {
    val context = LocalContext.current
    val playerManager = remember { PlayerManager.getInstance(context) }
    val playerViewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModelFactory(context.applicationContext as android.app.Application)
    )
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val currentLyrics by playerViewModel.currentLyrics.collectAsState()
    val currentLyricLines by playerViewModel.currentLyricLines.collectAsState()
    val searchResults by playerViewModel.searchResults.collectAsState()
    val isSearching by playerViewModel.isSearching.collectAsState()
    val isFetchingLyrics by playerViewModel.isFetchingLyrics.collectAsState()
    val lyricsMessage by playerViewModel.lyricsMessage.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()
    
    var showLyrics by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showEffectDialog by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 歌詞取得メッセージを表示
    LaunchedEffect(lyricsMessage) {
        lyricsMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "✕"
            )
            playerViewModel.clearLyricsMessage()
            // 歌詞取得が成功した場合、検索ダイアログを閉じる
            if (message.contains("取得しました") || message.contains("保存しました")) {
                kotlinx.coroutines.delay(500) // メッセージ表示後に閉じる
                showSearchDialog = false
            }
        }
    }
    
    // 現在の曲IDを監視
    val currentSongIdFlow by playerViewModel.currentSongIdFlow.collectAsState()
    
    // 初期読み込み（既に再生中の曲の場合は再生を開始しない）
    LaunchedEffect(songId) {
        // 既に再生中の曲の場合は曲情報と歌詞のみ読み込む
        val currentPlayingSongId = playerManager.currentSongId.value
        if (currentPlayingSongId == songId) {
            // 既に再生中の曲の場合は、曲情報と歌詞のみ読み込む
            playerViewModel.loadSongInfoOnly(songId)
        } else {
            // 新しい曲の場合は通常通り読み込む
            playerViewModel.loadSong(songId)
        }
    }
    
    // 現在の曲IDが変わったときに曲情報を更新（次の曲/前の曲ボタンで曲が変わった場合）
    LaunchedEffect(currentSongIdFlow) {
        currentSongIdFlow?.let { newSongId ->
            // 現在の曲と異なる場合のみ更新（無限ループを防ぐ）
            if (currentSong?.id != newSongId) {
                playerViewModel.loadSong(newSongId)
            }
        }
    }
    
    // 位置更新
    LaunchedEffect(Unit) {
        val playerManager = PlayerManager.getInstance(context)
        while (true) {
            playerManager.updatePosition()
            delay(Constants.POSITION_UPDATE_INTERVAL_MS)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("再生中") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "閉じる")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { snackbarData ->
                    Snackbar(
                        snackbarData = snackbarData,
                        actionOnNewLine = false
                    )
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 背景：アルバムアートをぼかして表示（歌詞がある場合のみ）
            val albumArtPath = currentSong?.albumArtPath
            if (showLyrics && albumArtPath != null && albumArtPath.isNotBlank()) {
                // タイムスタンプパラメータを削除して元のパスを取得（表示用）
                val imagePathForDisplay = albumArtPath.substringBefore("?t=").substringBefore("&t=")
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(50.dp)
                        .scale(1.2f)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("$imagePathForDisplay?t=${albumArtPath.hashCode()}") // タイムスタンプを含めてキャッシュを無効化
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                // 暗いオーバーレイ
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Black.copy(alpha = 0.8f),
                                    Color.Black.copy(alpha = 0.9f)
                                )
                            )
                        )
                )
            }
            
            // メインコンテンツ（再生コントロールなど）
            if (showLyrics) {
                // 歌詞表示時のレイアウト
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 上部：曲名とアーティスト名（固定領域）
                    Spacer(modifier = Modifier.height(16.dp))
                    LyricsHeaderSection(song = currentSong)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 中央：歌詞表示領域（タイトルとコントロールの間）
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentLyrics != null) {
                            LyricsBackgroundView(
                                currentLyricLines = currentLyricLines,
                                currentPosition = currentPosition,
                                currentLyrics = currentLyrics,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // 歌詞がない場合：歌詞取得UI
                            LyricsEmptyState(
                                isSearching = isSearching,
                                isFetchingLyrics = isFetchingLyrics,
                                onSearchClick = { showSearchDialog = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    
                    // 下部：再生コントロール（固定領域）
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // シークバー（通常モードと同じ順序）
                        SeekBarSection(
                            currentPosition = currentPosition,
                            duration = duration,
                            onSeek = { playerViewModel.seekTo(it.toLong()) }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 再生コントロール
                        PlaybackControlsSection(
                            isPlaying = isPlaying,
                            onPlayPause = { playerViewModel.playPause() },
                            onSkipNext = { playerViewModel.skipNext() },
                            onSkipPrevious = { playerViewModel.skipPrevious() }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // その他のコントロール
                        val hasLyrics1 = currentLyrics != null
                        AdditionalControlsSection(
                            hasLyrics = hasLyrics1,
                            showLyrics = showLyrics,
                            repeatMode = repeatMode,
                            onLyricsClick = { 
                                if (currentLyrics == null) {
                                    showSearchDialog = true
                                } else {
                                    showLyrics = !showLyrics
                                }
                            },
                            onShuffleClick = { playerViewModel.toggleShuffle() },
                            onRepeatClick = { playerViewModel.toggleRepeat() },
                            onEffectClick = { showEffectDialog = true },
                            onEditClick = { 
                                if (currentLyrics != null) {
                                    onEditLyrics()
                                }
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            } else {
                // 通常時のレイアウト
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // アルバムアート
                    AlbumArtSection(song = currentSong)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 曲情報
                    SongInfoSection(song = currentSong)
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // シークバー
                    SeekBarSection(
                        currentPosition = currentPosition,
                        duration = duration,
                        onSeek = { playerViewModel.seekTo(it.toLong()) }
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // 再生コントロール
                    PlaybackControlsSection(
                        isPlaying = isPlaying,
                        onPlayPause = { playerViewModel.playPause() },
                        onSkipNext = { playerViewModel.skipNext() },
                        onSkipPrevious = { playerViewModel.skipPrevious() }
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // その他のコントロール（上部と同じ内容を再表示）
                    val hasLyrics2 = currentLyrics != null
                    AdditionalControlsSection(
                        hasLyrics = hasLyrics2,
                        showLyrics = showLyrics,
                        repeatMode = repeatMode,
                        onLyricsClick = { 
                            if (currentLyrics == null) {
                                // 歌詞がない場合は検索ダイアログを開く
                                showSearchDialog = true
                            } else {
                                // 歌詞がある場合は表示/非表示を切り替え
                                showLyrics = !showLyrics
                            }
                        },
                        onShuffleClick = { playerViewModel.toggleShuffle() },
                        onRepeatClick = { playerViewModel.toggleRepeat() },
                        onEffectClick = { showEffectDialog = true },
                        onEditClick = { 
                            if (currentLyrics != null) {
                                onEditLyrics()
                            }
                        }
                    )
                }
            }
        }
        
        // 検索結果ダイアログ
        if (showSearchDialog) {
            LyricsSearchDialog(
                searchResults = searchResults,
                isSearching = isSearching,
                isFetchingLyrics = isFetchingLyrics,
                initialKeyword = currentSong?.title ?: "",
                onDismiss = { showSearchDialog = false },
                onSelectLyrics = { lyricsId ->
                    playerViewModel.fetchLyricsFromLRCLic(lyricsId)
                },
                onSearch = { keyword ->
                    playerViewModel.searchLyricsFromLRCLicByKeyword(keyword)
                }
            )
        }
        
        // エフェクト設定ダイアログ
        if (showEffectDialog) {
            AudioEffectDialog(
                onDismiss = { showEffectDialog = false }
            )
        }
        
        // 歌詞編集画面への遷移は直接onEditClickで処理
    }
}

@Composable
private fun LyricsSearchDialog(
    searchResults: List<LRCLicSearchResult>,
    isSearching: Boolean,
    isFetchingLyrics: Boolean = false,
    initialKeyword: String = "",
    onDismiss: () -> Unit,
    onSelectLyrics: (String) -> Unit,
    onSearch: (String) -> Unit
) {
    var keyword by remember { mutableStateOf(initialKeyword) }
    var hasSearched by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("歌詞を検索")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                // 検索キーワード
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { 
                        Text("キーワードで検索")
                    },
                    placeholder = {
                        Text("曲名など")
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 検索ボタン
                Button(
                    onClick = {
                        if (keyword.isNotBlank()) {
                            hasSearched = true
                            onSearch(keyword)
                        }
                    },
                    enabled = !isSearching && keyword.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("検索")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Divider()
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 歌詞取得中表示
                if (isFetchingLyrics) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "歌詞を取得中...",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                // 検索結果
                if (hasSearched && !isFetchingLyrics) {
                    if (isSearching) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (searchResults.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "歌詞が見つかりませんでした",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "検索結果: ${searchResults.size}件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults, key = { it.id }) { result ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isFetchingLyrics) { 
                                            onSelectLyrics(result.id) 
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = result.title,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = result.artist,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!result.album.isNullOrEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = result.album,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}

@Composable
private fun AlbumArtSection(song: com.example.soundwave.data.database.SongEntity?) {
    Box(
        modifier = Modifier
            .size(300.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // TODO: Coilでアルバムアートを表示
        Icon(
            Icons.Default.Album,
            contentDescription = "アルバムアート",
            modifier = Modifier.size(200.dp)
        )
    }
}

@Composable
private fun LyricsHeaderSection(
    song: com.example.soundwave.data.database.SongEntity?
) {
    song?.let {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = it.title,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it.artist,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SongInfoSection(song: com.example.soundwave.data.database.SongEntity?) {
    song?.let {
        Text(
            text = it.title,
            style = MaterialTheme.typography.headlineMedium,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${it.artist} - ${it.album}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SeekBarSection(
    currentPosition: Long,
    duration: Long,
    onSeek: (Float) -> Unit
) {
    Slider(
        value = currentPosition.toFloat(),
        onValueChange = onSeek,
        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
        modifier = Modifier.fillMaxWidth()
    )
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = TimeFormatter.format(currentPosition),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = TimeFormatter.format(duration),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PlaybackControlsSection(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onSkipPrevious) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "前の曲",
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        FloatingActionButton(onClick = onPlayPause) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "一時停止" else "再生"
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        IconButton(onClick = onSkipNext) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "次の曲",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun LyricsBackgroundView(
    currentLyricLines: List<LyricLine>,
    currentPosition: Long,
    currentLyrics: com.example.soundwave.data.database.LyricsEntity?,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // 現在の再生位置に基づいてスクロール位置を更新
    LaunchedEffect(currentPosition, currentLyricLines) {
        if (currentLyricLines.isNotEmpty()) {
            val currentIndex = currentLyricLines.indexOfFirst { it.time > currentPosition }
            if (currentIndex > 0) {
                listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0))
            }
        }
    }
    
    if (currentLyricLines.isNotEmpty()) {
        // LRC形式の歌詞（タイムスタンプ付き）
        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(currentLyricLines) { index, line ->
                val isCurrentLine = line.time <= currentPosition && 
                    (index == currentLyricLines.size - 1 || currentLyricLines[index + 1].time > currentPosition)
                
                Text(
                    text = line.text,
                    style = if (isCurrentLine) {
                        MaterialTheme.typography.headlineLarge.copy(
                            color = Color.White
                        )
                    } else {
                        MaterialTheme.typography.titleMedium.copy(
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            }
        }
    } else if (currentLyrics != null) {
        // プレーンテキストの歌詞
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(currentLyrics.lyricsText.lines()) { line ->
                if (line.isNotBlank()) {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.3f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsEmptyState(
    isSearching: Boolean,
    isFetchingLyrics: Boolean,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.TextFields,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.White.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "歌詞がありません",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "同じフォルダに.lrcファイルを配置すると\n自動的に読み込まれます",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        if (isFetchingLyrics) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "歌詞を取得中...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else {
            Button(
                onClick = onSearchClick,
                enabled = !isSearching,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("検索中...")
                } else {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LRCLicから検索")
                }
            }
        }
    }
}

@Composable
private fun AdditionalControlsSection(
    hasLyrics: Boolean = false,
    showLyrics: Boolean = false,
    repeatMode: com.example.soundwave.player.RepeatMode = com.example.soundwave.player.RepeatMode.NONE,
    onLyricsClick: () -> Unit = {},
    onShuffleClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    onEffectClick: () -> Unit = {},
    onEditClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        IconButton(onClick = onShuffleClick) {
            Icon(
                Icons.Default.Shuffle,
                contentDescription = "シャッフル",
                tint = if (repeatMode == com.example.soundwave.player.RepeatMode.SHUFFLE) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
        IconButton(onClick = onRepeatClick) {
            Icon(
                imageVector = when (repeatMode) {
                    com.example.soundwave.player.RepeatMode.REPEAT_ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                },
                contentDescription = when (repeatMode) {
                    com.example.soundwave.player.RepeatMode.REPEAT_ONE -> "1曲ループ"
                    com.example.soundwave.player.RepeatMode.REPEAT_ALL -> "すべてループ"
                    else -> "ループ"
                },
                tint = if (repeatMode == com.example.soundwave.player.RepeatMode.REPEAT_ONE || 
                           repeatMode == com.example.soundwave.player.RepeatMode.REPEAT_ALL) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
        IconButton(onClick = onLyricsClick) {
            Icon(
                Icons.Default.TextFields,
                contentDescription = "歌詞",
                tint = if (hasLyrics && showLyrics) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
        // 編集ボタン（歌詞がある場合のみ表示）
        if (hasLyrics) {
            IconButton(
                onClick = onEditClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "編集",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        IconButton(onClick = onEffectClick) {
            Icon(Icons.Default.GraphicEq, contentDescription = "エフェクト")
        }
    }
}

@Composable
private fun LyricsView(
    currentLyrics: com.example.soundwave.data.database.LyricsEntity?,
    currentLyricLines: List<LyricLine>,
    currentPosition: Long,
    currentSong: com.example.soundwave.data.database.SongEntity?,
    searchResults: List<LRCLicSearchResult>,
    isSearching: Boolean,
    isFetchingLyrics: Boolean,
    showSearchDialog: Boolean,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onSelectLyrics: (String) -> Unit,
    onSearch: (String) -> Unit,
    onDismissSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    
    // 現在の再生位置に基づいてスクロール位置を更新
    LaunchedEffect(currentPosition, currentLyricLines) {
        if (currentLyricLines.isNotEmpty()) {
            val currentIndex = currentLyricLines.indexOfFirst { it.time > currentPosition }
            if (currentIndex > 0) {
                listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0))
            }
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // 背景：アルバムアートをぼかして表示
        currentSong?.albumArtPath?.let { albumArtPath ->
            if (albumArtPath.isNotBlank()) {
                // タイムスタンプパラメータを削除して元のパスを取得（表示用）
                val imagePathForDisplay = albumArtPath.substringBefore("?t=").substringBefore("&t=")
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(50.dp)
                        .scale(1.2f)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("$imagePathForDisplay?t=${albumArtPath.hashCode()}") // タイムスタンプを含めてキャッシュを無効化
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
        
        // 暗いオーバーレイ
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
        )
        
        // コンテンツ
        Column(modifier = Modifier.fillMaxSize()) {
            // ヘッダー（上部に小さなアルバムアート、曲名、アーティスト名）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 小さなアルバムアート
                currentSong?.albumArtPath?.let { albumArtPath ->
                    if (albumArtPath.isNotBlank()) {
                        // タイムスタンプパラメータを削除して元のパスを取得（表示用）
                        val imagePathForDisplay = albumArtPath.substringBefore("?t=").substringBefore("&t=")
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("$imagePathForDisplay?t=${albumArtPath.hashCode()}") // タイムスタンプを含めてキャッシュを無効化
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(MaterialTheme.shapes.small),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }
                
                // 曲名とアーティスト名
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong?.title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong?.artist ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // メニューボタン（戻るボタン）
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "閉じる",
                        tint = Color.White
                    )
                }
            }
            
            // 歌詞表示
            if (currentLyrics != null) {
                if (currentLyricLines.isNotEmpty()) {
                    // LRC形式の歌詞（タイムスタンプ付き）
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(currentLyricLines) { index, line ->
                            val isCurrentLine = line.time <= currentPosition && 
                                (index == currentLyricLines.size - 1 || currentLyricLines[index + 1].time > currentPosition)
                            
                            Text(
                                text = line.text,
                                style = if (isCurrentLine) {
                                    MaterialTheme.typography.headlineMedium.copy(
                                        color = Color.White
                                    )
                                } else {
                                    MaterialTheme.typography.bodyLarge.copy(
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                },
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                } else {
                    // プレーンテキストの歌詞
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(currentLyrics.lyricsText.lines()) { line ->
                            if (line.isNotBlank()) {
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // 歌詞がない場合
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.TextFields,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "歌詞がありません",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "同じフォルダに.lrcファイルを配置すると\n自動的に読み込まれます",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onSearchClick,
                            enabled = !isSearching && currentSong != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            )
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("検索中...")
                            } else {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("LRCLicから検索")
                            }
                        }
                    }
                }
            }
        }
        
        // 検索結果ダイアログ
        if (showSearchDialog) {
            LyricsSearchDialog(
                searchResults = searchResults,
                isSearching = isSearching,
                isFetchingLyrics = isFetchingLyrics,
                initialKeyword = currentSong?.title ?: "",
                onDismiss = onDismissSearch,
                onSelectLyrics = onSelectLyrics,
                onSearch = onSearch
            )
        }
    }
}
