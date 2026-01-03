package com.example.soundwave.data.repository

import android.content.Context
import com.example.soundwave.data.database.LyricsDao
import com.example.soundwave.data.database.LyricsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File

data class LyricLine(
    val time: Long, // milliseconds
    val text: String
)

class LyricsRepository(
    private val lyricsDao: LyricsDao,
    private val context: Context
) {
    private val lrcLicApi = LRCLicApi()
    suspend fun getLyrics(songId: Long): LyricsEntity? {
        return lyricsDao.getLyricsBySongId(songId)
    }
    
    fun getLyricsFlow(songId: Long): Flow<LyricsEntity?> {
        return lyricsDao.getLyricsBySongIdFlow(songId)
    }
    
    suspend fun saveLyrics(songId: Long, lyricsText: String, lyricsLrc: String? = null, source: String = "manual"): Long {
        val existing = lyricsDao.getLyricsBySongId(songId)
        return if (existing != null) {
            val updated = existing.copy(
                lyricsText = lyricsText,
                lyricsLrc = lyricsLrc ?: existing.lyricsLrc,
                source = source,
                dateModified = System.currentTimeMillis()
            )
            lyricsDao.updateLyrics(updated)
            existing.id
        } else {
            lyricsDao.insertLyrics(
                LyricsEntity(
                    songId = songId,
                    lyricsText = lyricsText,
                    lyricsLrc = lyricsLrc,
                    source = source
                )
            )
        }
    }
    
    suspend fun deleteLyrics(songId: Long) {
        lyricsDao.deleteLyricsBySongId(songId)
    }
    
    // LRCファイルから歌詞を読み込む
    suspend fun loadLyricsFromFile(songFilePath: String, songId: Long): Boolean {
        try {
            val audioFile = File(songFilePath)
            val lrcFile = File(audioFile.parent, "${audioFile.nameWithoutExtension}.lrc")
            
            if (!lrcFile.exists()) {
                return false
            }
            
            val lrcContent = lrcFile.readText()
            val parsedLyrics = parseLrcFile(lrcContent)
            
            if (parsedLyrics.isNotEmpty()) {
                val lyricsText = parsedLyrics.joinToString("\n") { it.text }
                saveLyrics(songId, lyricsText, lrcContent, "file")
                return true
            }
        } catch (e: Exception) {
            android.util.Log.e("LyricsRepository", "Error loading lyrics from file", e)
        }
        return false
    }
    
    // LRC形式の歌詞をパース
    fun parseLrcFile(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
        
        lrcContent.lineSequence().forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val milliseconds = match.groupValues[3].toLong()
                val text = match.groupValues[4].trim()
                
                val timeMs = (minutes * 60 + seconds) * 1000 + milliseconds
                lines.add(LyricLine(timeMs, text))
            }
        }
        
        return lines.sortedBy { it.time }
    }
    
    // 現在の再生位置に基づいて表示する歌詞行を取得
    fun getCurrentLyricLine(lyrics: LyricsEntity?, currentPosition: Long): String? {
        if (lyrics == null) return null
        
        // LRC形式の歌詞がある場合は、タイムスタンプに基づいて取得
        if (!lyrics.lyricsLrc.isNullOrEmpty()) {
            val parsedLyrics = parseLrcFile(lyrics.lyricsLrc)
            val currentLine = parsedLyrics.lastOrNull { it.time <= currentPosition }
            return currentLine?.text
        }
        
        // プレーンテキストの場合は、全体を返す
        return lyrics.lyricsText
    }
    
    // 現在の再生位置に基づいて表示する歌詞行とその前後の行を取得
    fun getLyricLinesAround(lyrics: LyricsEntity?, currentPosition: Long, contextLines: Int = 2): List<LyricLine> {
        if (lyrics == null) return emptyList()
        
        // LRC形式の歌詞がある場合は、タイムスタンプに基づいて取得
        if (!lyrics.lyricsLrc.isNullOrEmpty()) {
            val parsedLyrics = parseLrcFile(lyrics.lyricsLrc)
            val currentIndex = parsedLyrics.indexOfLast { it.time <= currentPosition }
            
            if (currentIndex >= 0) {
                val startIndex = (currentIndex - contextLines).coerceAtLeast(0)
                val endIndex = (currentIndex + contextLines + 1).coerceAtMost(parsedLyrics.size)
                return parsedLyrics.subList(startIndex, endIndex)
            }
        }
        
        // プレーンテキストの場合は、行ごとに分割して返す
        return lyrics.lyricsText.lines().mapIndexed { index, line ->
            LyricLine(index * 1000L, line) // 仮のタイムスタンプ
        }
    }
    
    /**
     * LRCLicからキーワードで歌詞を検索
     * @param keyword 検索キーワード
     * @param searchType 検索タイプ（"q", "track_name", "artist_name", "album_name"）
     *                   "q": 任意のフィールドで検索（推奨）
     * @return 検索結果のリスト
     */
    suspend fun searchLyricsFromLRCLicByKeyword(
        keyword: String,
        searchType: String = "q"
    ): List<LRCLicSearchResult> {
        return lrcLicApi.searchLyricsByKeyword(keyword, searchType)
    }
    
    /**
     * LRCLicから歌詞を検索（従来の方法 - 後方互換性のため残す）
     * @param trackName 曲名
     * @param artistName アーティスト名
     * @param albumName アルバム名（オプション）
     * @return 検索結果のリスト
     */
    suspend fun searchLyricsFromLRCLic(
        trackName: String,
        artistName: String,
        albumName: String? = null
    ): List<LRCLicSearchResult> {
        return lrcLicApi.searchLyrics(trackName, artistName, albumName)
    }
    
    /**
     * LRCLicから歌詞を取得して保存
     * @param songId 曲ID
     * @param trackName 曲名
     * @param artistName アーティスト名
     * @param albumName アルバム名（オプション）
     * @return 取得に成功した場合true
     */
    suspend fun fetchAndSaveLyricsFromLRCLic(
        songId: Long,
        trackName: String,
        artistName: String,
        albumName: String? = null
    ): Boolean {
        return try {
            val lyrics = lrcLicApi.getLyricsDirect(trackName, artistName, albumName)
            if (lyrics != null) {
                val lyricsText = if (lyrics.plainText.isNotEmpty()) {
                    lyrics.plainText
                } else if (lyrics.lrc.isNotEmpty()) {
                    // LRC形式からテキストを抽出
                    parseLrcFile(lyrics.lrc).joinToString("\n") { it.text }
                } else {
                    ""
                }
                
                if (lyricsText.isNotEmpty()) {
                    saveLyrics(songId, lyricsText, lyrics.lrc.ifEmpty { null }, "lrclic")
                    return true
                }
            }
            false
        } catch (e: Exception) {
            android.util.Log.e("LyricsRepository", "Error fetching lyrics from LRCLic", e)
            false
        }
    }
    
    /**
     * LRCLicから歌詞IDを指定して歌詞を取得して保存
     * @param songId 曲ID
     * @param lyricsId 歌詞ID
     * @return 取得に成功した場合true
     */
    suspend fun fetchAndSaveLyricsFromLRCLicById(
        songId: Long,
        lyricsId: String
    ): Boolean {
        return try {
            val lyrics = lrcLicApi.getLyrics(lyricsId)
            if (lyrics != null) {
                val lyricsText = if (lyrics.plainText.isNotEmpty()) {
                    lyrics.plainText
                } else if (lyrics.lrc.isNotEmpty()) {
                    parseLrcFile(lyrics.lrc).joinToString("\n") { it.text }
                } else {
                    ""
                }
                
                if (lyricsText.isNotEmpty()) {
                    saveLyrics(songId, lyricsText, lyrics.lrc.ifEmpty { null }, "lrclic")
                    return true
                }
            }
            false
        } catch (e: Exception) {
            android.util.Log.e("LyricsRepository", "Error fetching lyrics from LRCLic by ID", e)
            false
        }
    }
}

