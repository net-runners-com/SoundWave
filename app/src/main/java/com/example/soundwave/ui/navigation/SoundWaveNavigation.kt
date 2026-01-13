package com.example.soundwave.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Snackbar
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.soundwave.data.AppDatabaseModule
import com.example.soundwave.player.PlayerManager
import com.example.soundwave.ui.permission.PermissionScreen
import com.example.soundwave.ui.album.AlbumDetailScreen
import com.example.soundwave.ui.artist.ArtistDetailScreen
import com.example.soundwave.ui.folder.FolderDetailScreen
import com.example.soundwave.ui.playlist.PlaylistDetailScreen
import com.example.soundwave.ui.home.HomeScreen
import com.example.soundwave.ui.player.PlayerScreen
import com.example.soundwave.ui.player.LyricsEditScreen
import com.example.soundwave.ui.settings.SettingsScreen
import com.example.soundwave.ui.settings.VersionHistoryScreen
import com.example.soundwave.ui.settings.WidgetSettingsScreen
import com.example.soundwave.ui.theme.AppTheme
import com.example.soundwave.ui.download.DownloadProgressManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.net.URLDecoder
import java.net.URLEncoder

// ナビゲーションルート定義
object SoundWaveRoutes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val WIDGET_SETTINGS = "widget_settings"
    const val VERSION_HISTORY = "version_history"
    const val PLAYER = "player/{songId}"
    const val LYRICS_EDIT = "lyrics_edit/{songId}"
    const val ALBUM = "album/{albumName}"
    const val ARTIST = "artist/{artistName}"
    const val FOLDER = "folder/{folderPath}"
    const val PLAYLIST = "playlist/{playlistId}"
    
    // ヘルパー関数
    fun player(songId: Long) = "player/$songId"
    fun lyricsEdit(songId: Long) = "lyrics_edit/$songId"
    fun album(albumName: String) = "album/${encodeParam(albumName)}"
    fun artist(artistName: String) = "artist/${encodeParam(artistName)}"
    fun folder(folderPath: String) = "folder/${encodeParam(folderPath)}"
    fun playlist(playlistId: Long) = "playlist/$playlistId"
    
    private fun encodeParam(param: String): String {
        return URLEncoder.encode(param, "UTF-8")
    }
    
    fun decodeParam(param: String): String {
        return URLDecoder.decode(param, "UTF-8")
    }
}

@Composable
fun SoundWaveNavigation(
    onThemeChanged: (AppTheme) -> Unit = {}
) {
    val context = LocalContext.current
    val playerManager = remember { PlayerManager.getInstance(context) }
    val musicRepository = remember { AppDatabaseModule.getMusicRepository(context) }
    val playlistRepository = remember { AppDatabaseModule.getPlaylistRepository(context) }
    
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    
    var hasPermissions by remember { mutableStateOf(false) }
    
    // ダウンロード状態を監視（全画面で表示）
    val isDownloading by DownloadProgressManager.isDownloading.collectAsState()
    val completionMessage by DownloadProgressManager.completionMessage.collectAsState()
    val downloadSnackbarHostState = remember { SnackbarHostState() }
    
    // ダウンロード中Snackbarを表示（全画面で継続表示）
    LaunchedEffect(isDownloading) {
        if (isDownloading) {
            try {
                downloadSnackbarHostState.currentSnackbarData?.dismiss()
                downloadSnackbarHostState.showSnackbar(
                    message = "ダウンロード中...",
                    duration = SnackbarDuration.Indefinite,
                    actionLabel = "✕"
                )
            } catch (e: Exception) {
                // Snackbar更新エラーは無視
            }
        } else {
            downloadSnackbarHostState.currentSnackbarData?.dismiss()
        }
    }
    
    // ダウンロード完了メッセージを表示
    LaunchedEffect(completionMessage) {
        completionMessage?.let { message ->
            try {
                downloadSnackbarHostState.currentSnackbarData?.dismiss()
                downloadSnackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short,
                    actionLabel = "✕"
                )
                // メッセージ表示後にクリア
                DownloadProgressManager.clearCompletionMessage()
            } catch (e: Exception) {
                // Snackbar更新エラーは無視
            }
        }
    }
    
    // 曲をクリックしたときの処理（再生のみ、画面遷移しない）
    val onSongClick: (Long) -> Unit = { songId ->
        coroutineScope.launch(Dispatchers.IO) {
            val song = musicRepository.getSongById(songId)
            song?.let {
                withContext(Dispatchers.Main) {
                    // 現在のルートに応じて適切なメソッドを呼び出す
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    when {
                        currentRoute?.startsWith("playlist/") == true -> {
                            val playlistId = currentRoute.substringAfter("playlist/").toLongOrNull()
                            playlistId?.let { id ->
                                val songs = playlistRepository.getSongsInPlaylist(id).first()
                                playerManager.playSongFromPlaylist(id, songs, it.filePath, it.id)
                            }
                        }
                        currentRoute?.startsWith("album/") == true -> {
                            val albumName = currentRoute.substringAfter("album/")
                            val decodedName = SoundWaveRoutes.decodeParam(albumName)
                            val songs = musicRepository.getSongsByAlbum(decodedName).first()
                            playerManager.playSongFromAlbum(decodedName, songs, it.filePath, it.id)
                        }
                        currentRoute?.startsWith("artist/") == true -> {
                            val artistName = currentRoute.substringAfter("artist/")
                            val decodedName = SoundWaveRoutes.decodeParam(artistName)
                            val songs = musicRepository.getSongsByArtist(decodedName).first()
                            playerManager.playSongFromArtist(decodedName, songs, it.filePath, it.id)
                        }
                        currentRoute?.startsWith("folder/") == true -> {
                            val folderPath = currentRoute.substringAfter("folder/")
                            val decodedPath = SoundWaveRoutes.decodeParam(folderPath)
                            val songs = musicRepository.getSongsByFolder(decodedPath).first()
                            playerManager.playSongFromFolder(decodedPath, songs, it.filePath, it.id)
                        }
                        else -> {
                            // 通常モード: コンテキストをクリア
                            playerManager.clearContextMode()
                            playerManager.playSong(it.filePath, it.id)
                        }
                    }
                }
            }
        }
    }
    
    if (!hasPermissions) {
        PermissionScreen(
            onPermissionsGranted = { hasPermissions = true }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = SoundWaveRoutes.HOME
            ) {
            composable(SoundWaveRoutes.HOME) {
                HomeScreen(
                    onSongSelected = onSongClick,
                    onAlbumSelected = { album -> 
                        navController.navigate(SoundWaveRoutes.album(album)) {
                            launchSingleTop = false
                        }
                    },
                    onArtistSelected = { artist -> 
                        navController.navigate(SoundWaveRoutes.artist(artist)) {
                            launchSingleTop = false
                        }
                    },
                    onFolderSelected = { folderPath -> 
                        navController.navigate(SoundWaveRoutes.folder(folderPath)) {
                            launchSingleTop = false
                        }
                    },
                    onPlaylistSelected = { playlistId -> 
                        navController.navigate(SoundWaveRoutes.playlist(playlistId)) {
                            launchSingleTop = false
                        }
                    },
                    onSettingsClick = { 
                        navController.navigate(SoundWaveRoutes.SETTINGS)
                    },
                    onSongDetail = { songId -> 
                        // ボトムシートで表示するため、ナビゲーションは不要
                    },
                    onEditLyrics = { songId ->
                        navController.navigate(SoundWaveRoutes.lyricsEdit(songId)) {
                            launchSingleTop = false
                        }
                    }
                )
            }
            
            composable(SoundWaveRoutes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onThemeChanged = onThemeChanged,
                    onWidgetSettingsClick = { 
                        navController.navigate(SoundWaveRoutes.WIDGET_SETTINGS)
                    },
                    onVersionHistoryClick = {
                        navController.navigate(SoundWaveRoutes.VERSION_HISTORY)
                    }
                )
            }
            
            composable(SoundWaveRoutes.WIDGET_SETTINGS) {
                WidgetSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            
            composable(SoundWaveRoutes.VERSION_HISTORY) {
                VersionHistoryScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            
            composable(
                route = SoundWaveRoutes.PLAYER,
                arguments = listOf(navArgument("songId") { type = NavType.LongType })
            ) { backStackEntry ->
                val songId = backStackEntry.arguments?.getLong("songId") ?: return@composable
                val onEditLyrics: () -> Unit = remember(songId) {
                    {
                        navController.navigate(SoundWaveRoutes.lyricsEdit(songId)) {
                            launchSingleTop = false
                        }
                    }
                }
                PlayerScreen(
                    songId = songId,
                    onBack = { navController.popBackStack() },
                    onEditLyrics = onEditLyrics
                )
            }
            
            composable(
                route = SoundWaveRoutes.LYRICS_EDIT,
                arguments = listOf(navArgument("songId") { type = NavType.LongType }),
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = androidx.compose.animation.core.tween(300)
                    )
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = androidx.compose.animation.core.tween(300)
                    )
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = androidx.compose.animation.core.tween(300)
                    )
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = androidx.compose.animation.core.tween(300)
                    )
                }
            ) { backStackEntry ->
                val songId = backStackEntry.arguments?.getLong("songId") ?: return@composable
                val lyricsRepository = remember { AppDatabaseModule.getLyricsRepository(context) }
                
                // 歌詞データを取得
                var lyrics by remember { mutableStateOf<com.example.soundwave.data.database.LyricsEntity?>(null) }
                var lyricLines by remember { mutableStateOf<List<com.example.soundwave.data.repository.LyricLine>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                
                LaunchedEffect(songId) {
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        lyrics = lyricsRepository.getLyrics(songId)
                        lyrics?.let { l ->
                            if (!l.lyricsLrc.isNullOrEmpty()) {
                                lyricLines = lyricsRepository.parseLrcFile(l.lyricsLrc)
                            }
                        }
                        isLoading = false
                    }
                }
                
                if (isLoading) {
                    // 読み込み中
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (lyrics != null) {
                    LyricsEditScreen(
                        lyrics = lyrics!!,
                        lyricLines = lyricLines,
                        onBack = {
                            // PlayerScreenに戻る
                            navController.navigate(SoundWaveRoutes.player(songId)) {
                                popUpTo(SoundWaveRoutes.LYRICS_EDIT) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onSave = { lyricsText: String, lyricsLrc: String? ->
                            coroutineScope.launch(Dispatchers.IO) {
                                lyricsRepository.saveLyrics(songId, lyricsText, lyricsLrc)
                                withContext(Dispatchers.Main) {
                                    // PlayerScreenに戻る
                                    navController.navigate(SoundWaveRoutes.player(songId)) {
                                        popUpTo(SoundWaveRoutes.LYRICS_EDIT) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                    )
                } else {
                    // 歌詞がない場合は戻る
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                }
            }
            
            composable(
                route = SoundWaveRoutes.ALBUM,
                arguments = listOf(navArgument("albumName") { type = NavType.StringType })
            ) { backStackEntry ->
                val albumName = backStackEntry.arguments?.getString("albumName")
                    ?.let { SoundWaveRoutes.decodeParam(it) } ?: return@composable
                
                AlbumDetailScreen(
                    albumName = albumName,
                    onBack = { 
                        // バックスタックが複数ある場合は前の画面に戻る、なければHOMEに戻る
                        if (!navController.popBackStack()) {
                            navController.navigate(SoundWaveRoutes.HOME) {
                                popUpTo(SoundWaveRoutes.HOME) { inclusive = true }
                            }
                        }
                    },
                    onSongSelected = onSongClick,
                    onAlbumSelected = { album -> 
                        navController.navigate(SoundWaveRoutes.album(album)) {
                            launchSingleTop = false
                        }
                    },
                    onArtistSelected = { artist -> 
                        navController.navigate(SoundWaveRoutes.artist(artist)) {
                            launchSingleTop = false
                        }
                    },
                    onFolderSelected = { folderPath -> 
                        navController.navigate(SoundWaveRoutes.folder(folderPath)) {
                            launchSingleTop = false
                        }
                    },
                    onPlaylistSelected = { playlistId -> 
                        navController.navigate(SoundWaveRoutes.playlist(playlistId)) {
                            launchSingleTop = false
                        }
                    },
                    onSongDetail = { songId -> 
                        // ボトムシートで表示するため、ナビゲーションは不要
                    },
                    onEditLyrics = { songId ->
                        navController.navigate(SoundWaveRoutes.lyricsEdit(songId)) {
                            launchSingleTop = false
                        }
                    }
                )
            }
            
            composable(
                route = SoundWaveRoutes.ARTIST,
                arguments = listOf(navArgument("artistName") { type = NavType.StringType })
            ) { backStackEntry ->
                val artistName = backStackEntry.arguments?.getString("artistName")
                    ?.let { SoundWaveRoutes.decodeParam(it) } ?: return@composable
                
                ArtistDetailScreen(
                    artistName = artistName,
                    onBack = { 
                        // バックスタックが複数ある場合は前の画面に戻る、なければHOMEに戻る
                        if (!navController.popBackStack()) {
                            navController.navigate(SoundWaveRoutes.HOME) {
                                popUpTo(SoundWaveRoutes.HOME) { inclusive = true }
                            }
                        }
                    },
                    onSongSelected = onSongClick,
                    onAlbumSelected = { album -> 
                        navController.navigate(SoundWaveRoutes.album(album)) {
                            launchSingleTop = false
                        }
                    },
                    onArtistSelected = { artist -> 
                        navController.navigate(SoundWaveRoutes.artist(artist)) {
                            launchSingleTop = false
                        }
                    },
                    onFolderSelected = { folderPath -> 
                        navController.navigate(SoundWaveRoutes.folder(folderPath)) {
                            launchSingleTop = false
                        }
                    },
                    onPlaylistSelected = { playlistId -> 
                        navController.navigate(SoundWaveRoutes.playlist(playlistId)) {
                            launchSingleTop = false
                        }
                    },
                    onEditLyrics = { songId ->
                        navController.navigate(SoundWaveRoutes.lyricsEdit(songId)) {
                            launchSingleTop = false
                        }
                    }
                )
            }
            
            composable(
                route = SoundWaveRoutes.FOLDER,
                arguments = listOf(navArgument("folderPath") { type = NavType.StringType })
            ) { backStackEntry ->
                val folderPath = backStackEntry.arguments?.getString("folderPath")
                    ?.let { SoundWaveRoutes.decodeParam(it) } ?: return@composable
                
                FolderDetailScreen(
                    folderPath = folderPath,
                    onBack = { 
                        // バックスタックが複数ある場合は前の画面に戻る、なければHOMEに戻る
                        if (!navController.popBackStack()) {
                            navController.navigate(SoundWaveRoutes.HOME) {
                                popUpTo(SoundWaveRoutes.HOME) { inclusive = true }
                            }
                        }
                    },
                    onSongSelected = onSongClick,
                    onAlbumSelected = { album -> 
                        navController.navigate(SoundWaveRoutes.album(album)) {
                            launchSingleTop = false
                        }
                    },
                    onArtistSelected = { artist -> 
                        navController.navigate(SoundWaveRoutes.artist(artist)) {
                            launchSingleTop = false
                        }
                    },
                    onFolderSelected = { path -> 
                        navController.navigate(SoundWaveRoutes.folder(path)) {
                            launchSingleTop = false
                        }
                    },
                    onPlaylistSelected = { playlistId -> 
                        navController.navigate(SoundWaveRoutes.playlist(playlistId)) {
                            launchSingleTop = false
                        }
                    },
                    onEditLyrics = { songId ->
                        navController.navigate(SoundWaveRoutes.lyricsEdit(songId)) {
                            launchSingleTop = false
                        }
                    }
                )
            }
            
            composable(
                route = SoundWaveRoutes.PLAYLIST,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
                
                PlaylistDetailScreen(
                    playlistId = playlistId,
                    onBack = { 
                        // バックスタックが複数ある場合は前の画面に戻る、なければHOMEに戻る
                        if (!navController.popBackStack()) {
                            navController.navigate(SoundWaveRoutes.HOME) {
                                popUpTo(SoundWaveRoutes.HOME) { inclusive = true }
                            }
                        }
                    },
                    onSongSelected = onSongClick,
                    onAlbumSelected = { album -> 
                        navController.navigate(SoundWaveRoutes.album(album)) {
                            launchSingleTop = false
                        }
                    },
                    onArtistSelected = { artist -> 
                        navController.navigate(SoundWaveRoutes.artist(artist)) {
                            launchSingleTop = false
                        }
                    },
                    onFolderSelected = { folderPath -> 
                        navController.navigate(SoundWaveRoutes.folder(folderPath)) {
                            launchSingleTop = false
                        }
                    },
                    onPlaylistSelected = { id -> 
                        navController.navigate(SoundWaveRoutes.playlist(id)) {
                            launchSingleTop = false
                        }
                    },
                    onEditLyrics = { songId ->
                        navController.navigate(SoundWaveRoutes.lyricsEdit(songId)) {
                            launchSingleTop = false
                        }
                    }
                )
            }
            } // End NavHost
            // Snackbarを再生中の曲表示の上に配置（オーバーレイとして配置）
            SnackbarHost(
                hostState = downloadSnackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp),
                snackbar = { snackbarData ->
                    Snackbar(
                        snackbarData = snackbarData,
                        actionOnNewLine = false
                    )
                }
            )
        } // End Box
    }
}

