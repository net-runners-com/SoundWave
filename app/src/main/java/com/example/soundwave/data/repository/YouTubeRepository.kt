package com.example.soundwave.data.repository

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class YouTubeVideo(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val channelName: String,
    val duration: String? = null
)

class YouTubeRepository(private val context: Context) {
    
    // YouTube検索（簡易実装：YouTube検索ページから情報を取得）
    // 注意: YouTubeのHTML構造が頻繁に変わるため、実際の運用ではYouTube Data API v3の使用を推奨
    suspend fun searchVideos(query: String, maxResults: Int = 20): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.youtube.com/results?search_query=$encodedQuery"
            
            val connection = URL(searchUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            
            val videos = mutableListOf<YouTubeVideo>()
            
            // ytInitialData から動画情報を抽出
            val ytInitialDataPattern = Regex("var ytInitialData = (\\{.*?\\});", RegexOption.DOT_MATCHES_ALL)
            val match = ytInitialDataPattern.find(response)
            
            if (match != null) {
                try {
                    val jsonStr = match.groupValues[1]
                    val json = JSONObject(jsonStr)
                    
                    // 複数のパスを試行して検索結果を取得
                    val contentsArray = tryGetContentsArray(json)
                    
                    contentsArray?.let { array ->
                        var foundCount = 0
                        // より多くのアイテムをチェック（maxResults * 3まで）
                        for (i in 0 until minOf(array.length(), maxResults * 3)) {
                            if (foundCount >= maxResults) break
                            
                            val video = extractVideoFromItem(array.optJSONObject(i))
                            video?.let {
                                videos.add(it)
                                foundCount++
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("YouTubeRepository", "Error parsing search results", e)
                }
            }
            
            // フォールバック: 正規表現で直接videoIdを抽出
            if (videos.isEmpty()) {
                android.util.Log.d("YouTubeRepository", "Trying fallback method: regex extraction")
                val videoIdPattern = Regex("\"videoId\":\"([^\"]+)\"")
                val titlePattern = Regex("\"title\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"")
                val channelPattern = Regex("\"ownerText\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"")
                
                val videoIds = videoIdPattern.findAll(response).map { it.groupValues[1] }.distinct().take(maxResults).toList()
                val titles = titlePattern.findAll(response).map { it.groupValues[1] }.take(maxResults).toList()
                val channels = channelPattern.findAll(response).map { it.groupValues[1] }.take(maxResults).toList()
                
                for (i in videoIds.indices) {
                    if (i < maxResults) {
                        videos.add(
                            YouTubeVideo(
                                id = videoIds[i],
                                title = titles.getOrNull(i) ?: "タイトル不明",
                                thumbnailUrl = "https://img.youtube.com/vi/${videoIds[i]}/hqdefault.jpg",
                                channelName = channels.getOrNull(i) ?: "チャンネル不明"
                            )
                        )
                    }
                }
            }
            
            videos
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepository", "Error searching videos", e)
            emptyList()
        }
    }
    
    // 複数のパスを試行してcontents配列を取得
    private fun tryGetContentsArray(json: JSONObject): JSONArray? {
        // パス1: 標準的な検索結果パス
        val path1 = json.optJSONObject("contents")
            ?.optJSONObject("twoColumnSearchResultsRenderer")
            ?.optJSONObject("primaryContents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
        if (path1 != null) return path1
        
        // パス2: 代替パス
        val path2 = json.optJSONObject("contents")
            ?.optJSONObject("twoColumnSearchResultsRenderer")
            ?.optJSONObject("primaryContents")
            ?.optJSONArray("contents")
        if (path2 != null) return path2
        
        // パス3: 別の構造
        val path3 = json.optJSONArray("contents")
        if (path3 != null) return path3
        
        return null
    }
    
    // アイテムから動画情報を抽出
    private fun extractVideoFromItem(item: JSONObject?): YouTubeVideo? {
        if (item == null) return null
        
        // 複数の方法でvideoRendererを取得
        val videoRenderer = item.optJSONObject("videoRenderer")
            ?: item.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")?.optJSONObject(0)?.optJSONObject("videoRenderer")
            ?: item.optJSONArray("contents")?.optJSONObject(0)?.optJSONObject("videoRenderer")
        
        if (videoRenderer == null) return null
        
        val videoId = videoRenderer.optString("videoId", "")
        if (videoId.isEmpty()) return null
        
        val titleObj = videoRenderer.optJSONObject("title")
        val title = titleObj?.optJSONArray("runs")
            ?.optJSONObject(0)?.optString("text", "")
            ?: titleObj?.optString("simpleText", "")
            ?: ""
        
        if (title.isEmpty()) return null
        
        val ownerText = videoRenderer.optJSONObject("ownerText")
        val channelName = ownerText?.optJSONArray("runs")
            ?.optJSONObject(0)?.optString("text", "")
            ?: ownerText?.optString("simpleText", "")
            ?: "チャンネル不明"
        
        val thumbnail = videoRenderer.optJSONObject("thumbnail")
        val thumbnailUrl = thumbnail?.optJSONArray("thumbnails")
            ?.optJSONObject(0)?.optString("url", "")
            ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        
        return YouTubeVideo(
            id = videoId,
            title = title,
            thumbnailUrl = thumbnailUrl,
            channelName = channelName
        )
    }
    
    // YouTube動画のURLを取得
    fun getVideoUrl(videoId: String): String {
        return "https://www.youtube.com/watch?v=$videoId"
    }
    
    // YouTube動画の埋め込みURLを取得
    fun getEmbedUrl(videoId: String): String {
        return "https://www.youtube.com/embed/$videoId"
    }
    
    // YouTubeDLの初期化（初回のみ）
    private var isYoutubeDLInitialized = false
    
    private suspend fun initializeYoutubeDL() = withContext(Dispatchers.IO) {
        if (isYoutubeDLInitialized) {
            return@withContext Unit
        }
        
        try {
            // 既にApplicationで初期化されている可能性があるので、try-catchで処理
            YoutubeDL.getInstance().init(context)
            isYoutubeDLInitialized = true
            android.util.Log.d("YouTubeRepository", "YoutubeDL initialized")
        } catch (e: com.yausername.youtubedl_android.YoutubeDLException) {
            // 既に初期化済みの場合はエラーを無視
            if (e.message?.contains("already initialized") == true || 
                e.message?.contains("initialized") == true) {
                isYoutubeDLInitialized = true
                android.util.Log.d("YouTubeRepository", "YoutubeDL already initialized")
            } else {
                android.util.Log.e("YouTubeRepository", "Failed to initialize YoutubeDL: ${e.message}", e)
                // 初期化エラーを再スロー（ダウンロードを試みない）
                throw e
            }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepository", "Unexpected error initializing YoutubeDL: ${e.message}", e)
            throw e
        }
        Unit // 明示的にUnitを返す
    }
    
    // YouTube動画をダウンロード
    suspend fun downloadVideo(
        videoId: String,
        format: String,
        quality: String,
        onProgress: (Float, Long) -> Unit = { _, _ -> }
    ): String? = withContext(Dispatchers.IO) {
        try {
            // YoutubeDLを初期化
            initializeYoutubeDL()
            
            val videoUrl = getVideoUrl(videoId)
            android.util.Log.d("YouTubeRepository", "Downloading: $videoUrl")
            
            // ダウンロード先ディレクトリ
            val externalFilesDir = context.getExternalFilesDir(null)
            if (externalFilesDir == null) {
                android.util.Log.e("YouTubeRepository", "External files directory is null")
                return@withContext null
            }
            
            val downloadDir = File(externalFilesDir, "Downloads")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            // リクエストを作成
            val request = YoutubeDLRequest(videoUrl)
            val outputPath = "${downloadDir.absolutePath}/%(title)s.%(ext)s"
            request.addOption("-o", outputPath)
            request.addOption("--no-playlist")
            request.addOption("--no-warnings")
            
            // 形式と品質に応じてオプションを設定
            when (format) {
                "MP3" -> {
                    request.addOption("-x")
                    request.addOption("--extract-audio")
                    request.addOption("--audio-format", "mp3")
                    request.addOption("--audio-quality", "0") // 最高品質
                }
                "MP4" -> {
                    when (quality) {
                        "BEST" -> request.addOption("-f", "best")
                        "HD_1080" -> request.addOption("-f", "bestvideo[height<=1080]+bestaudio/best[height<=1080]")
                        "HD_720" -> request.addOption("-f", "bestvideo[height<=720]+bestaudio/best[height<=720]")
                        "SD_480" -> request.addOption("-f", "bestvideo[height<=480]+bestaudio/best[height<=480]")
                        "SD_360" -> request.addOption("-f", "bestvideo[height<=360]+bestaudio/best[height<=360]")
                    }
                }
                "WEBM" -> {
                    request.addOption("-f", "webm")
                }
            }
            
            // ダウンロード実行
            val response = YoutubeDL.getInstance().execute(request) { progress, etaInSeconds ->
                onProgress(progress, etaInSeconds)
            }
            
            android.util.Log.d("YouTubeRepository", "Download response: ${response.out}")
            
            // ダウンロードされたファイルを検索
            val files = downloadDir.listFiles()
            val downloadedFile = files?.maxByOrNull { it.lastModified() }
            
            if (downloadedFile != null && downloadedFile.exists()) {
                android.util.Log.d("YouTubeRepository", "Downloaded file: ${downloadedFile.absolutePath}")
                downloadedFile.absolutePath
            } else {
                android.util.Log.e("YouTubeRepository", "Downloaded file not found")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepository", "Download error: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }
}

