package com.example.soundwave.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import com.example.soundwave.ui.settings.SettingsScreen
import com.example.soundwave.ui.settings.WidgetSettingsScreen
import com.example.soundwave.ui.song.SongDetailScreen
import com.example.soundwave.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.net.URLDecoder
import java.net.URLEncoder

// ナビゲーションルート定義
object SoundWaveRoutes {
    const val HOME = "home"
    const val SONG_DETAIL = "song_detail/{songId}"
    const val SETTINGS = "settings"
    const val WIDGET_SETTINGS = "widget_settings"
    const val PLAYER = "player/{songId}"
    const val ALBUM = "album/{albumName}"
    const val ARTIST = "artist/{artistName}"
    const val FOLDER = "folder/{folderPath}"
    const val PLAYLIST = "playlist/{playlistId}"
    
    // ヘルパー関数
    fun songDetail(songId: Long) = "song_detail/$songId"
    fun player(songId: Long) = "player/$songId"
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
        NavHost(
            navController = navController,
            startDestination = SoundWaveRoutes.HOME
        ) {
            composable(SoundWaveRoutes.HOME) {
                HomeScreen(
                    onSongSelected = onSongClick,
                    onAlbumSelected = { album -> 
                        navController.navigate(SoundWaveRoutes.album(album))
                    },
                    onArtistSelected = { artist -> 
                        navController.navigate(SoundWaveRoutes.artist(artist))
                    },
                    onFolderSelected = { folderPath -> 
                        navController.navigate(SoundWaveRoutes.folder(folderPath))
                    },
                    onPlaylistSelected = { playlistId -> 
                        navController.navigate(SoundWaveRoutes.playlist(playlistId))
                    },
                    onSettingsClick = { 
                        navController.navigate(SoundWaveRoutes.SETTINGS)
                    },
                    onSongDetail = { songId -> 
                        navController.navigate(SoundWaveRoutes.songDetail(songId))
                    }
                )
            }
            
            composable(
                route = SoundWaveRoutes.SONG_DETAIL,
                arguments = listOf(navArgument("songId") { type = NavType.LongType })
            ) { backStackEntry ->
                val songId = backStackEntry.arguments?.getLong("songId") ?: return@composable
                SongDetailScreen(
                    songId = songId,
                    onBack = { navController.popBackStack() }
                )
            }
            
            composable(SoundWaveRoutes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onThemeChanged = onThemeChanged,
                    onWidgetSettingsClick = { 
                        navController.navigate(SoundWaveRoutes.WIDGET_SETTINGS)
                    }
                )
            }
            
            composable(SoundWaveRoutes.WIDGET_SETTINGS) {
                WidgetSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            
            composable(
                route = SoundWaveRoutes.PLAYER,
                arguments = listOf(navArgument("songId") { type = NavType.LongType })
            ) { backStackEntry ->
                val songId = backStackEntry.arguments?.getLong("songId") ?: return@composable
                PlayerScreen(
                    songId = songId,
                    onBack = { navController.popBackStack() }
                )
            }
            
            composable(
                route = SoundWaveRoutes.ALBUM,
                arguments = listOf(navArgument("albumName") { type = NavType.StringType })
            ) { backStackEntry ->
                val albumName = backStackEntry.arguments?.getString("albumName")
                    ?.let { SoundWaveRoutes.decodeParam(it) } ?: return@composable
                
                AlbumDetailScreen(
                    albumName = albumName,
                    onBack = { navController.popBackStack() },
                    onSongSelected = onSongClick,
                    onAlbumSelected = { album -> 
                        navController.navigate(SoundWaveRoutes.album(album))
                    },
                    onArtistSelected = { artist -> 
                        navController.navigate(SoundWaveRoutes.artist(artist))
                    },
                    onFolderSelected = { folderPath -> 
                        navController.navigate(SoundWaveRoutes.folder(folderPath))
                    },
                    onPlaylistSelected = { playlistId -> 
                        navController.navigate(SoundWaveRoutes.playlist(playlistId))
                    },
                    onSongDetail = { songId -> 
                        navController.navigate(SoundWaveRoutes.songDetail(songId))
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
                    onBack = { navController.popBackStack() },
                    onSongSelected = onSongClick,
                    onAlbumSelected = { album -> 
                        navController.navigate(SoundWaveRoutes.album(album))
                    },
                    onArtistSelected = { artist -> 
                        navController.navigate(SoundWaveRoutes.artist(artist))
                    },
                    onFolderSelected = { folderPath -> 
                        navController.navigate(SoundWaveRoutes.folder(folderPath))
                    },
                    onPlaylistSelected = { playlistId -> 
                        navController.navigate(SoundWaveRoutes.playlist(playlistId))
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
                    onBack = { navController.popBackStack() },
                    onSongSelected = onSongClick,
                    onAlbumSelected = { album -> 
                        navController.navigate(SoundWaveRoutes.album(album))
                    },
                    onArtistSelected = { artist -> 
                        navController.navigate(SoundWaveRoutes.artist(artist))
                    },
                    onFolderSelected = { path -> 
                        navController.navigate(SoundWaveRoutes.folder(path))
                    },
                    onPlaylistSelected = { playlistId -> 
                        navController.navigate(SoundWaveRoutes.playlist(playlistId))
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
                    onBack = { navController.popBackStack() },
                    onSongSelected = onSongClick,
                    onAlbumSelected = { album -> 
                        navController.navigate(SoundWaveRoutes.album(album))
                    },
                    onArtistSelected = { artist -> 
                        navController.navigate(SoundWaveRoutes.artist(artist))
                    },
                    onFolderSelected = { folderPath -> 
                        navController.navigate(SoundWaveRoutes.folder(folderPath))
                    },
                    onPlaylistSelected = { id -> 
                        navController.navigate(SoundWaveRoutes.playlist(id))
                    }
                )
            }
        }
    }
}

