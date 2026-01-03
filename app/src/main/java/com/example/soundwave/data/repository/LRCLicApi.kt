package com.example.soundwave.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LRCLicSearchResult(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null
)

data class LRCLicLyrics(
    val lrc: String,
    val plainText: String
)

class LRCLicApi {
    companion object {
        private const val BASE_URL = "https://lrclib.net/api"
        private const val SEARCH_ENDPOINT = "$BASE_URL/search"
        private const val GET_LYRICS_ENDPOINT = "$BASE_URL/get"
    }
    
    /**
     * キーワードで歌詞を検索
     * LRCLib APIドキュメント: https://lrclib.net/docs
     * エンドポイント: GET /api/search
     * 
     * @param keyword 検索キーワード
     * @param searchType 検索タイプ（q, track_name, artist_name, album_name）
     *                   q: 任意のフィールド（曲名、アーティスト名、アルバム名）で検索
     * @return 検索結果のリスト
     */
    suspend fun searchLyricsByKeyword(
        keyword: String,
        searchType: String = "q"
    ): List<LRCLicSearchResult> = withContext(Dispatchers.IO) {
        try {
            if (keyword.isBlank()) {
                return@withContext emptyList()
            }
            
            val encodedKeyword = URLEncoder.encode(keyword.trim(), "UTF-8")
            // API仕様に基づいてパラメータを設定
            // q または track_name の少なくとも1つが必須
            val queryParams = when (searchType) {
                "q" -> {
                    // キーワード検索: 任意のフィールドで検索
                    "q=$encodedKeyword"
                }
                "track_name" -> {
                    // 曲名で検索
                    "track_name=$encodedKeyword"
                }
                "artist_name" -> {
                    // アーティスト名で検索（track_nameも必須のため、qを使用）
                    "q=$encodedKeyword"
                }
                "album_name" -> {
                    // アルバム名で検索（track_nameも必須のため、qを使用）
                    "q=$encodedKeyword"
                }
                else -> "q=$encodedKeyword"
            }
            
            val url = URL("$SEARCH_ENDPOINT?$queryParams")
            android.util.Log.d("LRCLicApi", "Search URL: $url")
            
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "SoundWave/1.0")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            android.util.Log.d("LRCLicApi", "Response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                
                android.util.Log.d("LRCLicApi", "Response: $response")
                
                if (response.isBlank()) {
                    return@withContext emptyList()
                }
                
                val jsonArray = JSONArray(response)
                val results = mutableListOf<LRCLicSearchResult>()
                
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val id = item.optString("id", "") ?: item.optLong("id", 0).toString()
                    val title = item.optString("trackName", "") ?: item.optString("track_name", "") ?: item.optString("name", "")
                    val artist = item.optString("artistName", "") ?: item.optString("artist_name", "") ?: item.optString("artist", "")
                    val album = item.optString("albumName", null) ?: item.optString("album_name", null) ?: item.optString("album", null)
                    
                    if (id.isNotEmpty() && title.isNotEmpty()) {
                        results.add(
                            LRCLicSearchResult(
                                id = id,
                                title = title,
                                artist = artist.ifEmpty { "Unknown Artist" },
                                album = album
                            )
                        )
                    }
                }
                
                android.util.Log.d("LRCLicApi", "Found ${results.size} results")
                return@withContext results
            } else {
                val errorResponse = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (e: Exception) {
                    ""
                }
                android.util.Log.e("LRCLicApi", "Search failed with code: $responseCode, response: $errorResponse")
                connection.disconnect()
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("LRCLicApi", "Error searching lyrics", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * 歌詞を検索（従来の方法 - 後方互換性のため残す）
     * LRCLib APIドキュメント: https://lrclib.net/docs
     * エンドポイント: GET /api/search
     * パラメータ: track_name, artist_name, album_name (オプション)
     * 
     * @param trackName 曲名
     * @param artistName アーティスト名
     * @param albumName アルバム名（オプション）
     * @return 検索結果のリスト
     */
    suspend fun searchLyrics(
        trackName: String,
        artistName: String,
        albumName: String? = null
    ): List<LRCLicSearchResult> = withContext(Dispatchers.IO) {
        try {
            // パラメータが空の場合は検索しない
            if (trackName.isBlank() && artistName.isBlank()) {
                return@withContext emptyList()
            }
            
            val queryParams = buildString {
                if (trackName.isNotBlank()) {
                    append("track_name=${URLEncoder.encode(trackName.trim(), "UTF-8")}")
                }
                if (artistName.isNotBlank()) {
                    if (isNotEmpty()) append("&")
                    append("artist_name=${URLEncoder.encode(artistName.trim(), "UTF-8")}")
                }
                if (!albumName.isNullOrBlank()) {
                    if (isNotEmpty()) append("&")
                    append("album_name=${URLEncoder.encode(albumName.trim(), "UTF-8")}")
                }
            }
            
            val url = URL("$SEARCH_ENDPOINT?$queryParams")
            android.util.Log.d("LRCLicApi", "Search URL: $url")
            
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "SoundWave/1.0")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            android.util.Log.d("LRCLicApi", "Response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                
                android.util.Log.d("LRCLicApi", "Response: $response")
                
                // レスポンスが空の場合は空リストを返す
                if (response.isBlank()) {
                    return@withContext emptyList()
                }
                
                val jsonArray = JSONArray(response)
                val results = mutableListOf<LRCLicSearchResult>()
                
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    // LRCLib APIのレスポンス形式に合わせてフィールド名を確認
                    val id = item.optString("id", "") ?: item.optLong("id", 0).toString()
                    val title = item.optString("trackName", "") ?: item.optString("track_name", "") ?: item.optString("name", "")
                    val artist = item.optString("artistName", "") ?: item.optString("artist_name", "") ?: item.optString("artist", "")
                    val album = item.optString("albumName", null) ?: item.optString("album_name", null) ?: item.optString("album", null)
                    
                    if (id.isNotEmpty() && title.isNotEmpty()) {
                        results.add(
                            LRCLicSearchResult(
                                id = id,
                                title = title,
                                artist = artist.ifEmpty { "Unknown Artist" },
                                album = album
                            )
                        )
                    }
                }
                
                android.util.Log.d("LRCLicApi", "Found ${results.size} results")
                return@withContext results
            } else {
                val errorResponse = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (e: Exception) {
                    ""
                }
                android.util.Log.e("LRCLicApi", "Search failed with code: $responseCode, response: $errorResponse")
                connection.disconnect()
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("LRCLicApi", "Error searching lyrics", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * 歌詞IDから歌詞を取得（リトライ機能付き）
     * LRCLib APIドキュメント: https://lrclib.net/docs
     * エンドポイント: GET /api/get?id={id}
     * 
     * @param lyricsId 歌詞ID
     * @param maxRetries 最大リトライ回数（デフォルト: 3）
     * @return 歌詞データ（LRC形式とプレーンテキスト）
     */
    suspend fun getLyrics(lyricsId: String, maxRetries: Int = 3): LRCLicLyrics? = withContext(Dispatchers.IO) {
        if (lyricsId.isBlank()) {
            return@withContext null
        }
        
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                val url = URL("$GET_LYRICS_ENDPOINT?id=$lyricsId")
                android.util.Log.d("LRCLicApi", "Get lyrics URL: $url (attempt ${attempt + 1}/$maxRetries)")
                
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "SoundWave/1.0")
                connection.setRequestProperty("Connection", "close")
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                
                // 接続を確立
                connection.connect()
                
                val responseCode = try {
                    connection.responseCode
                } catch (e: IOException) {
                    android.util.Log.w("LRCLicApi", "Failed to get response code (attempt ${attempt + 1}): ${e.message}")
                    connection.disconnect()
                    lastException = e
                    if (attempt < maxRetries - 1) {
                        delay((1000 * (attempt + 1)).toLong()) // 指数バックオフ
                        return@repeat
                    } else {
                        return@withContext null
                    }
                }
                
                android.util.Log.d("LRCLicApi", "Get lyrics response code: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = try {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } catch (e: IOException) {
                        android.util.Log.w("LRCLicApi", "Failed to read response (attempt ${attempt + 1}): ${e.message}")
                        connection.disconnect()
                        lastException = e
                        if (attempt < maxRetries - 1) {
                            delay((1000 * (attempt + 1)).toLong())
                            return@repeat
                        } else {
                            return@withContext null
                        }
                    } finally {
                        connection.disconnect()
                    }
                    
                    if (response.isBlank()) {
                        android.util.Log.w("LRCLicApi", "Empty response")
                        return@withContext null
                    }
                    
                    val json = try {
                        JSONObject(response)
                    } catch (e: Exception) {
                        android.util.Log.e("LRCLicApi", "Failed to parse JSON: ${e.message}")
                        return@withContext null
                    }
                    
                    // LRCLib APIのレスポンス形式に合わせてフィールド名を確認
                    val lrc = json.optString("syncedLyrics", "") 
                        ?: json.optString("synced_lyrics", "")
                        ?: json.optString("lrc", "")
                    val plainText = json.optString("plainLyrics", "")
                        ?: json.optString("plain_lyrics", "")
                        ?: json.optString("plain", "")
                    
                    if (lrc.isNotEmpty() || plainText.isNotEmpty()) {
                        android.util.Log.d("LRCLicApi", "Lyrics retrieved: LRC=${lrc.isNotEmpty()}, Plain=${plainText.isNotEmpty()}")
                        return@withContext LRCLicLyrics(
                            lrc = lrc,
                            plainText = plainText
                        )
                    } else {
                        android.util.Log.w("LRCLicApi", "Lyrics data is empty")
                        return@withContext null
                    }
                } else {
                    val errorResponse = try {
                        connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    android.util.Log.e("LRCLicApi", "Get lyrics failed with code: $responseCode, response: $errorResponse")
                    connection.disconnect()
                    return@withContext null
                }
            } catch (e: IOException) {
                android.util.Log.w("LRCLicApi", "Network error (attempt ${attempt + 1}/$maxRetries): ${e.message}")
                lastException = e
                if (attempt < maxRetries - 1) {
                    delay((1000 * (attempt + 1)).toLong()) // 指数バックオフ: 1秒、2秒、3秒...
                }
            } catch (e: Exception) {
                android.util.Log.e("LRCLicApi", "Unexpected error getting lyrics (attempt ${attempt + 1}): ${e.message}", e)
                lastException = e
                return@withContext null
            }
        }
        
        // すべてのリトライが失敗した場合
        android.util.Log.e("LRCLicApi", "Failed to get lyrics after $maxRetries attempts", lastException)
        return@withContext null
    }
    
    /**
     * 曲名とアーティスト名から直接歌詞を取得（最初の検索結果を使用）
     * @param trackName 曲名
     * @param artistName アーティスト名
     * @param albumName アルバム名（オプション）
     * @return 歌詞データ
     */
    suspend fun getLyricsDirect(
        trackName: String,
        artistName: String,
        albumName: String? = null
    ): LRCLicLyrics? {
        val searchResults = searchLyrics(trackName, artistName, albumName)
        if (searchResults.isNotEmpty()) {
            return getLyrics(searchResults[0].id)
        }
        return null
    }
}

