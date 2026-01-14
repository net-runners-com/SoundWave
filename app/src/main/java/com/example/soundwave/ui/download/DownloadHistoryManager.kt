package com.example.soundwave.ui.download

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

data class DownloadItem(
    val id: String,
    val videoTitle: String,
    val videoId: String,
    val format: String,
    val quality: String,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val filePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

enum class DownloadStatus {
    PENDING,    // 待機中
    DOWNLOADING, // ダウンロード中
    PAUSED,     // 一時停止
    COMPLETED,   // 完了
    FAILED,      // 失敗
    CANCELLED    // キャンセル
}

/**
 * ダウンロード履歴を管理するシングルトン
 */
object DownloadHistoryManager {
    private val _downloadHistory = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloadHistory: StateFlow<List<DownloadItem>> = _downloadHistory.asStateFlow()
    
    private val _activeDownloads = MutableStateFlow<Map<String, kotlinx.coroutines.Job>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, kotlinx.coroutines.Job>> = _activeDownloads.asStateFlow()
    
    private var prefs: SharedPreferences? = null
    
    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences("download_history", Context.MODE_PRIVATE)
            loadHistory()
        }
    }
    
    private fun loadHistory() {
        prefs?.let { prefs ->
            val historyJson = prefs.getString("history", "[]") ?: "[]"
            try {
                val list = mutableListOf<DownloadItem>()
                val jsonArray = JSONArray(historyJson)
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    list.add(
                        DownloadItem(
                            id = item.getString("id"),
                            videoTitle = item.getString("videoTitle"),
                            videoId = item.getString("videoId"),
                            format = item.getString("format"),
                            quality = item.getString("quality"),
                            status = DownloadStatus.valueOf(item.getString("status")),
                            progress = item.optDouble("progress", 0.0).toFloat(),
                            filePath = item.optString("filePath", null).takeIf { it.isNotEmpty() },
                            createdAt = item.getLong("createdAt"),
                            completedAt = item.optLong("completedAt", -1).takeIf { it > 0 }
                        )
                    )
                }
                _downloadHistory.value = list.sortedByDescending { it.createdAt }
            } catch (e: Exception) {
                android.util.Log.e("DownloadHistoryManager", "Error loading history", e)
                _downloadHistory.value = emptyList()
            }
        }
    }
    
    suspend fun addDownload(item: DownloadItem) = withContext(Dispatchers.IO) {
        val currentHistory = _downloadHistory.value.toMutableList()
        // 既に存在する場合は削除
        currentHistory.removeAll { it.id == item.id }
        // 先頭に追加
        currentHistory.add(0, item)
        // 最大100件まで保持
        if (currentHistory.size > 100) {
            currentHistory.removeAt(currentHistory.size - 1)
        }
        _downloadHistory.value = currentHistory.sortedByDescending { it.createdAt }
        saveHistory()
    }
    
    suspend fun updateDownload(id: String, status: DownloadStatus, progress: Float = 0f, filePath: String? = null) = withContext(Dispatchers.IO) {
        val currentHistory = _downloadHistory.value.toMutableList()
        val index = currentHistory.indexOfFirst { it.id == id }
        if (index >= 0) {
            val item = currentHistory[index]
            currentHistory[index] = item.copy(
                status = status,
                progress = progress,
                filePath = filePath,
                completedAt = if (status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED || status == DownloadStatus.CANCELLED || status == DownloadStatus.PAUSED) {
                    System.currentTimeMillis()
                } else item.completedAt
            )
            _downloadHistory.value = currentHistory.sortedByDescending { it.createdAt }
            saveHistory()
        }
    }
    
    fun setActiveDownload(id: String, job: kotlinx.coroutines.Job) {
        val current = _activeDownloads.value.toMutableMap()
        current[id] = job
        _activeDownloads.value = current
    }
    
    fun removeActiveDownload(id: String) {
        val current = _activeDownloads.value.toMutableMap()
        current.remove(id)
        _activeDownloads.value = current
    }
    
    suspend fun cancelDownload(id: String): Boolean = withContext(Dispatchers.IO) {
        val job = _activeDownloads.value[id]
        return@withContext if (job != null && job.isActive) {
            job.cancel()
            removeActiveDownload(id)
            updateDownload(id, DownloadStatus.CANCELLED)
            true
        } else {
            false
        }
    }
    
    suspend fun pauseDownload(id: String): Boolean = withContext(Dispatchers.IO) {
        val job = _activeDownloads.value[id]
        return@withContext if (job != null && job.isActive) {
            job.cancel()
            removeActiveDownload(id)
            updateDownload(id, DownloadStatus.PAUSED)
            true
        } else {
            false
        }
    }
    
    suspend fun resumeDownload(id: String, videoId: String, format: String, quality: String, videoTitle: String): String = withContext(Dispatchers.IO) {
        // 新しいダウンロードIDを生成
        val newDownloadId = "${videoId}_${System.currentTimeMillis()}"
        val downloadItem = DownloadItem(
            id = newDownloadId,
            videoTitle = videoTitle,
            videoId = videoId,
            format = format,
            quality = quality,
            status = DownloadStatus.DOWNLOADING,
            progress = 0f
        )
        addDownload(downloadItem)
        return@withContext newDownloadId
    }
    
    fun isDownloading(id: String): Boolean {
        return _activeDownloads.value[id]?.isActive == true
    }
    
    private fun saveHistory() {
        prefs?.let { prefs ->
            try {
                val jsonArray = JSONArray()
                _downloadHistory.value.forEach { item ->
                    val json = JSONObject().apply {
                        put("id", item.id)
                        put("videoTitle", item.videoTitle)
                        put("videoId", item.videoId)
                        put("format", item.format)
                        put("quality", item.quality)
                        put("status", item.status.name)
                        put("progress", item.progress)
                        put("filePath", item.filePath ?: "")
                        put("createdAt", item.createdAt)
                        put("completedAt", item.completedAt ?: -1)
                    }
                    jsonArray.put(json)
                }
                prefs.edit().putString("history", jsonArray.toString()).apply()
            } catch (e: Exception) {
                android.util.Log.e("DownloadHistoryManager", "Error saving history", e)
            }
        }
    }
    
    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        _downloadHistory.value = emptyList()
        prefs?.edit()?.putString("history", "[]")?.apply()
    }
    
    suspend fun removeDownload(id: String) = withContext(Dispatchers.IO) {
        val currentHistory = _downloadHistory.value.toMutableList()
        currentHistory.removeAll { it.id == id }
        _downloadHistory.value = currentHistory.sortedByDescending { it.createdAt }
        saveHistory()
        // アクティブなダウンロードも削除
        removeActiveDownload(id)
    }
}
