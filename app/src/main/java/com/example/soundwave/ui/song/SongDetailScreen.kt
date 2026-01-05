package com.example.soundwave.ui.song

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.yalantis.ucrop.UCrop
import java.io.File
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.soundwave.data.AppDatabaseModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.soundwave.util.TimeFormatter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    songId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val musicRepository = remember { AppDatabaseModule.getMusicRepository(context) }
    val scope = rememberCoroutineScope()
    
    var song by remember { mutableStateOf<com.example.soundwave.data.database.SongEntity?>(null) }
    var isEditingTitle by remember { mutableStateOf(false) }
    var isEditingArtist by remember { mutableStateOf(false) }
    var editedTitle by remember { mutableStateOf("") }
    var editedArtist by remember { mutableStateOf("") }
    
    // クロッピング後のLauncher
    val cropImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val resultUri = UCrop.getOutput(result.data ?: return@rememberLauncherForActivityResult)
            resultUri?.let { croppedUri ->
                scope.launch(Dispatchers.IO) {
                    // クロッピングされた画像のパスを取得して保存
                    val imagePath = croppedUri.toString()
                    song?.let { currentSong ->
                        val updatedSong = currentSong.copy(albumArtPath = imagePath)
                        musicRepository.updateSong(updatedSong)
                        song = updatedSong
                    }
                }
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val cropError = UCrop.getError(result.data ?: return@rememberLauncherForActivityResult)
            android.util.Log.e("SongDetailScreen", "Crop error: ${cropError?.message}")
        }
    }
    
    // 画像選択用のLauncher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { sourceUri ->
            // クロッピング用の一時ファイルを作成
            val destinationUri = Uri.fromFile(
                File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
            )
            
            // UCropオプションを設定
            val options = UCrop.Options().apply {
                setToolbarTitle("画像をクロップ")
                setCompressionQuality(90)
                setHideBottomControls(false)
                setFreeStyleCropEnabled(true)
            }
            
            // UCropを起動
            val uCrop = UCrop.of(sourceUri, destinationUri)
                .withOptions(options)
                .withAspectRatio(1f, 1f) // 正方形
                .withMaxResultSize(2048, 2048)
            
            activity?.let {
                cropImageLauncher.launch(uCrop.getIntent(it))
            }
        }
    }
    
    // 曲情報を読み込む
    LaunchedEffect(songId) {
        withContext(Dispatchers.IO) {
            song = musicRepository.getSongById(songId)
            song?.let {
                editedTitle = it.title
                editedArtist = it.artist
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("曲の詳細") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { paddingValues ->
        song?.let { currentSong ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // アルバムアート（クリックで変更可能）
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { imagePickerLauncher.launch("image/*") }
                ) {
                    if (currentSong.albumArtPath != null && currentSong.albumArtPath.isNotBlank()) {
                        val imagePainter = rememberAsyncImagePainter(
                            model = ImageRequest.Builder(context)
                                .data(currentSong.albumArtPath)
                                .crossfade(true)
                                .build()
                        )
                        
                        when (imagePainter.state) {
                            is coil.compose.AsyncImagePainter.State.Loading,
                            is coil.compose.AsyncImagePainter.State.Error -> {
                                // 読み込みエラーまたはローディング中はデフォルトアイコンを表示
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = "アルバムアートなし",
                                        modifier = Modifier.size(80.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            else -> {
                                AsyncImage(
                                    model = imagePainter.request,
                                    contentDescription = "アルバムアート",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "アルバムアートなし",
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    // 編集アイコンをオーバーレイ
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "画像を変更",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // タイトル（インライン編集可能）
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    if (isEditingTitle) {
                        OutlinedTextField(
                            value = editedTitle,
                            onValueChange = { editedTitle = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = {
                                        isEditingTitle = false
                                        editedTitle = currentSong.title
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "キャンセル")
                                    }
                                    IconButton(onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            val updatedSong = currentSong.copy(title = editedTitle)
                                            musicRepository.updateSong(updatedSong)
                                            song = updatedSong
                                            isEditingTitle = false
                                        }
                                    }) {
                                        Icon(Icons.Default.Check, contentDescription = "保存")
                                    }
                                }
                            }
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clickable { isEditingTitle = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "タイトル",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentSong.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "編集",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // アーティスト（インライン編集可能）
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    if (isEditingArtist) {
                        OutlinedTextField(
                            value = editedArtist,
                            onValueChange = { editedArtist = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = {
                                        isEditingArtist = false
                                        editedArtist = currentSong.artist
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "キャンセル")
                                    }
                                    IconButton(onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            val updatedSong = currentSong.copy(artist = editedArtist)
                                            musicRepository.updateSong(updatedSong)
                                            song = updatedSong
                                            isEditingArtist = false
                                        }
                                    }) {
                                        Icon(Icons.Default.Check, contentDescription = "保存")
                                    }
                                }
                            }
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clickable { isEditingArtist = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "アーティスト",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentSong.artist,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "編集",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // アルバム
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "アルバム",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentSong.album,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                
                // 曲の長さ
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "長さ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = TimeFormatter.format(currentSong.duration),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                
                // 追加日
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "追加日",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
                                .format(Date(currentSong.dateAdded)),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        } ?: run {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

