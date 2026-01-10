package com.example.soundwave.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionHistoryScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("バージョン履歴") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            VersionHistoryItem(
                version = "1.0.0",
                date = "2024年1月1日",
                items = listOf(
                    "初回リリース",
                    "基本的な音楽再生機能",
                    "プレイリスト機能",
                    "アルバム・アーティスト表示"
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            VersionHistoryItem(
                version = "1.1.0",
                date = "2024年2月1日",
                items = listOf(
                    "位置情報ベースの音楽再生機能を追加",
                    "マップ上で円を描画して音楽を自動再生",
                    "YouTube動画のダウンロード機能を追加"
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            VersionHistoryItem(
                version = "1.2.0",
                date = "2024年3月1日",
                items = listOf(
                    "UIデザインの改善",
                    "曲詳細情報の表示機能を追加",
                    "設定画面の機能拡張"
                )
            )
        }
    }
}

@Composable
private fun VersionHistoryItem(
    version: String,
    date: String,
    items: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth()
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
                Text(
                    text = version,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "・",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

