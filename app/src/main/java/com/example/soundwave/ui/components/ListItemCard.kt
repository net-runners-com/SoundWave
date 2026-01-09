@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.soundwave.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

@Composable
fun ListItemCard(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    content: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 画像の遅延読み込み (Coil)
            if (imageUrl != null && imageUrl.isNotBlank()) {
                // タイムスタンプパラメータを削除して元のパスを取得（表示用）
                val imagePathForDisplay = imageUrl.substringBefore("?t=").substringBefore("&t=")
                // ファイルパスからFileオブジェクトを作成して使用
                val imageFile = java.io.File(imagePathForDisplay)
                val imagePainter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageFile)
                        .crossfade(true)
                        .build()
                )
                
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .padding(end = 12.dp)
                        .clip(MaterialTheme.shapes.medium)
                ) {
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
                                    contentDescription = title,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        else -> {
                            AsyncImage(
                                model = imagePainter.request,
                                contentDescription = title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
            content?.invoke()
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}



