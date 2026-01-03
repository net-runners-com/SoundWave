@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.soundwave.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    onBack: () -> Unit
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
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 歌詞取得メッセージを表示
    LaunchedEffect(lyricsMessage) {
        lyricsMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            playerViewModel.clearLyricsMessage()
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
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        if (showLyrics) {
            LyricsView(
                currentLyrics = currentLyrics,
                currentLyricLines = currentLyricLines,
                currentPosition = currentPosition,
                currentSong = currentSong,
                searchResults = searchResults,
                isSearching = isSearching,
                isFetchingLyrics = isFetchingLyrics,
                showSearchDialog = showSearchDialog,
                onBack = { showLyrics = false },
                onSearchClick = {
                    showSearchDialog = true
                    playerViewModel.searchLyricsFromLRCLic()
                },
                onSelectLyrics = { lyricsId ->
                    playerViewModel.fetchLyricsFromLRCLic(lyricsId)
                    showSearchDialog = false
                },
                onSearch = { keyword, searchType ->
                    playerViewModel.searchLyricsFromLRCLicByKeyword(keyword, searchType)
                },
                onDismissSearch = { showSearchDialog = false },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
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
                
                // その他のコントロール
                AdditionalControlsSection(
                    hasLyrics = currentLyrics != null,
                    repeatMode = repeatMode,
                    onLyricsClick = { showLyrics = true },
                    onShuffleClick = { playerViewModel.toggleShuffle() },
                    onRepeatClick = { playerViewModel.toggleRepeat() }
                )
            }
        }
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
    onSearch: (String, String) -> Unit
) {
    var keyword by remember { mutableStateOf(initialKeyword) }
    var searchType by remember { mutableStateOf("q") }
    var hasSearched by remember { mutableStateOf(false) }
    
    val searchTypeOptions = listOf(
        "q" to "キーワード",
        "track_name" to "曲名",
        "artist_name" to "アーティスト名",
        "album_name" to "アルバム名"
    )
    
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
                // 検索タイプ選択
                Text(
                    text = "検索タイプ",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    searchTypeOptions.forEach { (type, label) ->
                        FilterChip(
                            selected = searchType == type,
                            onClick = { searchType = type },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 検索キーワード
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { 
                        Text(
                            when (searchType) {
                                "q" -> "キーワードで検索"
                                "track_name" -> "曲名で検索"
                                "artist_name" -> "アーティスト名で検索"
                                "album_name" -> "アルバム名で検索"
                                else -> "キーワードで検索"
                            }
                        )
                    },
                    placeholder = {
                        Text(
                            when (searchType) {
                                "q" -> "例: 曲名、アーティスト名、アルバム名など"
                                "track_name" -> "例: 曲名を入力"
                                "artist_name" -> "例: アーティスト名を入力"
                                "album_name" -> "例: アルバム名を入力"
                                else -> "例: キーワードを入力"
                            }
                        )
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
                            onSearch(keyword, searchType)
                        }
                    },
                    enabled = !isSearching && keyword.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
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
                        Text("検索")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Divider()
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 歌詞取得中表示
                if (isFetchingLyrics) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "歌詞を取得中...",
                            style = MaterialTheme.typography.bodyMedium
                        )
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
private fun SongInfoSection(song: com.example.soundwave.data.database.SongEntity?) {
    song?.let {
        Text(
            text = it.title,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${it.artist} - ${it.album}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            Icon(Icons.Default.SkipPrevious, contentDescription = "前の曲")
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
            Icon(Icons.Default.SkipNext, contentDescription = "次の曲")
        }
    }
}

@Composable
private fun AdditionalControlsSection(
    hasLyrics: Boolean = false,
    repeatMode: com.example.soundwave.player.RepeatMode = com.example.soundwave.player.RepeatMode.NONE,
    onLyricsClick: () -> Unit = {},
    onShuffleClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {}
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
                tint = if (hasLyrics) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
        IconButton(onClick = { /* TODO: エフェクト */ }) {
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
    onSearch: (String, String) -> Unit,
    onDismissSearch: () -> Unit,
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
    
    Column(modifier = modifier) {
        // ヘッダー
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "歌詞",
                style = MaterialTheme.typography.titleLarge
            )
        }
        
        Divider()
        
        // 歌詞表示
        if (currentLyrics != null) {
            if (currentLyricLines.isNotEmpty()) {
                // LRC形式の歌詞（タイムスタンプ付き）
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(currentLyricLines) { index, line ->
                        val isCurrentLine = line.time <= currentPosition && 
                            (index == currentLyricLines.size - 1 || currentLyricLines[index + 1].time > currentPosition)
                        
                        Text(
                            text = line.text,
                            style = if (isCurrentLine) {
                                MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            } else {
                // プレーンテキストの歌詞
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentLyrics.lyricsText,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.TextFields,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "歌詞がありません",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "同じフォルダに.lrcファイルを配置すると\n自動的に読み込まれます",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onSearchClick,
                        enabled = !isSearching && currentSong != null
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
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
