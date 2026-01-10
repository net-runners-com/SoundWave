package com.example.soundwave.ui.home.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.soundwave.data.AppDatabaseModule
import java.io.File
import com.example.soundwave.ui.components.EmptyState
import com.example.soundwave.ui.components.ListItemCard
import com.example.soundwave.ui.components.CreatePlaylistDialog
import com.example.soundwave.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongsTab(
    viewModel: HomeViewModel,
    onSongSelected: (Long) -> Unit,
    onSelectionModeChanged: (Boolean) -> Unit = {},
    onSelectedSongsChanged: (Set<Long>) -> Unit = {},
    onShowPlaylistOptions: () -> Unit = {},
    externalClearSelection: Boolean = false,
    onSongDetail: (Long) -> Unit = {},
    onAlbumSelected: (String) -> Unit = {},
    onArtistSelected: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val playlistRepository = remember { AppDatabaseModule.getPlaylistRepository(context) }
    val scope = rememberCoroutineScope()
    
    var searchQuery by remember { mutableStateOf("") }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showPlaylistOptions by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    
    // 外部から選択をクリアする
    LaunchedEffect(externalClearSelection) {
        if (externalClearSelection) {
            selectedSongs = emptySet()
            isSelectionMode = false
        }
    }
    
    // 選択モードが変更されたときにコールバックを呼び出す
    LaunchedEffect(isSelectionMode) {
        onSelectionModeChanged(isSelectionMode)
        // 選択モードが解除されたら選択をクリア
        if (!isSelectionMode) {
            selectedSongs = emptySet()
        }
    }
    
    // 選択中の曲がなくなったら自動で選択モードを解除
    LaunchedEffect(selectedSongs) {
        onSelectedSongsChanged(selectedSongs)
        if (selectedSongs.isEmpty() && isSelectionMode) {
            isSelectionMode = false
        }
    }
    
    // データをcollectAsStateで監視（既にキャッシュされているので再読み込みなし）
    val songs by viewModel.songs.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    
    // 検索フィルタリング
    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        // 検索バー
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("曲を検索") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "クリア")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )
        
        // 初回のみローディング表示、データが既にある場合はスキップ
        if (isLoading && songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (songs.isEmpty()) {
            EmptyState(
                message = "音楽ファイルが見つかりません",
                actionLabel = "再スキャン",
                onAction = { viewModel.scanMusicFiles() }
            )
        } else if (filteredSongs.isEmpty()) {
            EmptyState(message = "検索結果が見つかりませんでした")
        } else {
            // LazyColumnの状態を保持してスクロール位置を維持
            val listState = rememberLazyListState()
            
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredSongs, key = { it.id }) { song ->
                    val isSelected = selectedSongs.contains(song.id)
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedSongs = if (isSelected) {
                                            selectedSongs - song.id
                                        } else {
                                            selectedSongs + song.id
                                        }
                                    } else {
                                        onSongSelected(song.id)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedSongs = setOf(song.id)
                                    }
                                }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected && isSelectionMode) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // アルバムアートまたはデフォルトアイコン
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .padding(end = 12.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            ) {
                                if (song.albumArtPath != null && song.albumArtPath.isNotBlank()) {
                                    // タイムスタンプパラメータを削除して元のパスを取得（表示用）
                                    val imagePathForDisplay = song.albumArtPath.substringBefore("?t=").substringBefore("&t=")
                                    // ファイルパスからFileオブジェクトを作成して使用
                                    val imageFile = File(imagePathForDisplay)
                                    val imagePainter = rememberAsyncImagePainter(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(imageFile)
                                            .crossfade(true)
                                            .build()
                                    )
                                    
                                    when (imagePainter.state) {
                                        is AsyncImagePainter.State.Loading,
                                        is AsyncImagePainter.State.Error -> {
                                            // デフォルトアイコンをテーマカラーで表示
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = song.title,
                                                    modifier = Modifier.size(32.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        else -> {
                                            AsyncImage(
                                                model = imagePainter.request,
                                                contentDescription = song.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                } else {
                                    // アルバムアートがない場合はデフォルトアイコンを表示
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = song.title,
                                            modifier = Modifier.size(32.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${song.artist} - ${song.album}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            // ケバブメニュー
                            var showMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { showMenu = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "メニュー"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    if (song.album.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("アルバムへ") },
                                            onClick = {
                                                showMenu = false
                                                onAlbumSelected(song.album)
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Album, contentDescription = null)
                                            }
                                        )
                                    }
                                    if (song.artist.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("アーティストへ") },
                                            onClick = {
                                                showMenu = false
                                                onArtistSelected(song.artist)
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Person, contentDescription = null)
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("詳細") },
                                        onClick = {
                                            showMenu = false
                                            onSongDetail(song.id)
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Info, contentDescription = null)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }
        
        // 選択モード時のオプションボタン
        if (isSelectionMode && selectedSongs.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showPlaylistOptions = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "オプション"
                )
            }
        }
    }
    
    // プレイリストオプションメニュー
    if (showPlaylistOptions) {
        PlaylistOptionsMenu(
            selectedSongs = selectedSongs,
            onDismiss = { showPlaylistOptions = false },
            onCreatePlaylist = {
                showPlaylistOptions = false
                showCreatePlaylistDialog = true
            },
            onAddToPlaylist = {
                showPlaylistOptions = false
                showAddToPlaylistDialog = true
            }
        )
    }
    
    // プレイリスト作成ダイアログ
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { playlistName ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val playlistId = playlistRepository.createPlaylist(playlistName)
                        selectedSongs.forEachIndexed { index, songId ->
                            playlistRepository.addSongToPlaylist(playlistId, songId, index)
                        }
                    }
                    showCreatePlaylistDialog = false
                    isSelectionMode = false
                    selectedSongs = emptySet()
                }
            }
        )
    }
    
    // プレイリスト選択ダイアログ
    if (showAddToPlaylistDialog) {
        SelectPlaylistDialog(
            selectedSongs = selectedSongs,
            onDismiss = { showAddToPlaylistDialog = false },
            onPlaylistSelected = { playlistId ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val currentSongs = playlistRepository.getSongsInPlaylist(playlistId).first()
                        selectedSongs.forEachIndexed { index, songId ->
                            playlistRepository.addSongToPlaylist(playlistId, songId, currentSongs.size + index)
                        }
                    }
                    showAddToPlaylistDialog = false
                    isSelectionMode = false
                    selectedSongs = emptySet()
                }
            }
        )
    }
}

