package com.example.soundwave.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.soundwave.R
import com.example.soundwave.data.database.LyricsEntity
import com.example.soundwave.data.repository.LyricLine

/**
 * 歌詞編集画面（字幕編集風の表形式UI）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsEditScreen(
    lyrics: LyricsEntity,
    lyricLines: List<LyricLine>,
    onBack: () -> Unit,
    onSave: (String, String?) -> Unit
) {
    // LRC形式の歌詞行を編集可能な状態に変換
    var editableLines by remember {
        mutableStateOf(
            if (lyricLines.isNotEmpty()) {
                lyricLines.map { EditableLyricLine(it.time, it.text) }
            } else {
                // LRC形式がない場合は、プレーンテキストから行ごとに分割
                lyrics.lyricsText.lines().mapIndexed { index, line ->
                    EditableLyricLine(index * 1000L, line)
                }
            }
        )
    }
    
    // LRC形式の文字列を生成
    fun generateLrcString(): String {
        return editableLines
            .filter { it.text.isNotBlank() }
            .joinToString("\n") { line ->
                val minutes = line.time / 60000
                val seconds = (line.time % 60000) / 1000
                val milliseconds = line.time % 1000
                val mm = String.format("%02d", minutes)
                val ss = String.format("%02d", seconds)
                val ms = String.format("%03d", milliseconds)
                "[$mm:$ss.$ms]${line.text}"
            }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lyrics_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val lrcString = generateLrcString()
                            val plainTextFromLrc = editableLines
                                .filter { it.text.isNotBlank() }
                                .joinToString("\n") { it.text }
                            onSave(plainTextFromLrc, lrcString)
                            onBack()
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ヘッダー行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Start列
                Text(
                    text = stringResource(R.string.lyrics_edit_start),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(0.25f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                // End列
                Text(
                    text = stringResource(R.string.lyrics_edit_end),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(0.25f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                // Subtitle列
                Text(
                    text = stringResource(R.string.lyrics_edit_subtitle),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(0.5f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                // 削除ボタンのスペース（データ行と揃えるため）
                Spacer(modifier = Modifier.size(40.dp))
            }
            
            // 下線（オレンジ色）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            
            // 歌詞行のリスト
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                itemsIndexed(
                    items = editableLines,
                    key = { index, line -> "${index}_${line.time}_${line.text.hashCode()}" }
                ) { index, line ->
                    val endTime = if (index < editableLines.size - 1) {
                        editableLines[index + 1].time
                    } else {
                        line.time + 3000L // 最後の行は3秒後を終了時刻とする
                    }
                    
                    EditableLyricLineRow(
                        line = line,
                        endTime = endTime,
                        index = index,
                        onTimeChange = { newTime ->
                            editableLines = editableLines.toMutableList().apply {
                                this[index] = this[index].copy(time = newTime)
                            }
                        },
                        onTextChange = { newText ->
                            editableLines = editableLines.toMutableList().apply {
                                this[index] = this[index].copy(text = newText)
                            }
                        },
                        onDelete = {
                            editableLines = editableLines.toMutableList().apply {
                                removeAt(index)
                            }
                        }
                    )
                }
                
                // 行を追加ボタン
                item {
                    Button(
                        onClick = {
                            val lastTime = editableLines.lastOrNull()?.time ?: 0L
                            editableLines = editableLines.toMutableList().apply {
                                add(EditableLyricLine(lastTime + 3000L, ""))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.lyrics_edit_add_line))
                    }
                }
            }
        }
    }
}

/**
 * 編集可能な歌詞行のデータクラス
 */
internal data class EditableLyricLine(
    var time: Long, // milliseconds
    var text: String
)

/**
 * 歌詞行の編集可能な行（表形式）
 */
@Composable
private fun EditableLyricLineRow(
    line: EditableLyricLine,
    endTime: Long,
    index: Int,
    onTimeChange: (Long) -> Unit,
    onTextChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor = remember(index, colorScheme) {
        if (index % 2 == 0) {
            colorScheme.surface
        } else {
            colorScheme.surfaceVariant.copy(alpha = 0.3f)
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Start列（開始時刻）
        TimeDisplayField(
            timeMs = line.time,
            onTimeChange = onTimeChange,
            modifier = Modifier.weight(0.25f)
        )
        
        // End列（終了時刻 - 読み取り専用、次の行の開始時刻）
        TimeDisplayField(
            timeMs = endTime,
            onTimeChange = {}, // 読み取り専用
            modifier = Modifier
                .weight(0.25f)
                .alpha(0.6f),
            enabled = false
        )
        
        // Subtitle列（歌詞テキスト）
        OutlinedTextField(
            value = line.text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(0.5f),
            placeholder = { Text(stringResource(R.string.lyrics_edit_lyrics_hint)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        
        // 削除ボタン
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 時刻表示・編集フィールド（MM:SS.mmm形式）
 */
@Composable
private fun TimeDisplayField(
    timeMs: Long,
    onTimeChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val timeString = remember(timeMs) {
        val minutes = timeMs / 60000
        val seconds = (timeMs % 60000) / 1000
        val milliseconds = timeMs % 1000
        String.format("%02d:%02d.%03d", minutes, seconds, milliseconds)
    }
    
    if (enabled) {
        var timeText by remember(timeMs) { mutableStateOf(timeString) }
        
        // timeMsが変更されたときにtimeTextを更新
        androidx.compose.runtime.SideEffect {
            timeText = timeString
        }
        
        OutlinedTextField(
            value = timeText,
            onValueChange = { newValue ->
                // MM:SS.mmm形式の入力を受け付ける
                val regex = Regex("^\\d{0,2}:?\\d{0,2}\\.?\\d{0,3}$")
                if (newValue.matches(regex) || newValue.isEmpty()) {
                    timeText = newValue
                    // パースしてミリ秒に変換
                    parseTimeString(newValue)?.let { parsedTime ->
                        onTimeChange(parsedTime)
                    }
                }
            },
            modifier = modifier,
            placeholder = { Text("00:00.000") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            enabled = enabled
        )
    } else {
        // 読み取り専用表示
        Text(
            text = timeString,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
                .padding(horizontal = 8.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/**
 * 時刻文字列（MM:SS.mmm形式）をミリ秒に変換
 */
private fun parseTimeString(timeString: String): Long? {
    if (timeString.isEmpty()) return null
    
    try {
        val parts = timeString.split(":")
        if (parts.size != 2) return null
        
        val minutes = parts[0].toLongOrNull() ?: return null
        val secondsAndMs = parts[1].split(".")
        
        val seconds = if (secondsAndMs.isNotEmpty()) {
            secondsAndMs[0].toLongOrNull() ?: 0L
        } else {
            0L
        }
        
        val milliseconds = if (secondsAndMs.size > 1) {
            // ミリ秒部分を3桁に正規化
            val msStr = secondsAndMs[1].padEnd(3, '0').take(3)
            msStr.toLongOrNull() ?: 0L
        } else {
            0L
        }
        
        return minutes * 60000 + seconds * 1000 + milliseconds
    } catch (e: Exception) {
        return null
    }
}

