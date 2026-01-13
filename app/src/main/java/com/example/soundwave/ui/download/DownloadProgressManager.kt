package com.example.soundwave.ui.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ダウンロード進捗をアプリ全体で管理するシングルトン
 * どの画面でも進捗を表示できる
 */
object DownloadProgressManager {
    private val _isDownloading = MutableStateFlow(false)
    private val _completionMessage = MutableStateFlow<String?>(null)

    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()
    val completionMessage: StateFlow<String?> = _completionMessage.asStateFlow()

    /**
     * ダウンロード開始
     */
    fun startDownload() {
        _isDownloading.value = true
        _completionMessage.value = null
    }

    /**
     * ダウンロード完了
     */
    fun finishDownload(message: String? = null) {
        _isDownloading.value = false
        _completionMessage.value = message
    }

    /**
     * ダウンロードキャンセル/エラー
     */
    fun cancelDownload() {
        _isDownloading.value = false
        _completionMessage.value = null
    }

    /**
     * 完了メッセージをクリア
     */
    fun clearCompletionMessage() {
        _completionMessage.value = null
    }
}

