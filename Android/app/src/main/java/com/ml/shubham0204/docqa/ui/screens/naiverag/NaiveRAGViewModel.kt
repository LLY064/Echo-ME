package com.ml.shubham0204.docqa.ui.screens.naiverag

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ml.shubham0204.docqa.data.ChunksDB
import com.ml.shubham0204.docqa.data.LLMManager
import com.ml.shubham0204.docqa.domain.SentenceEmbeddingProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.koin.android.annotation.KoinViewModel
import java.io.File
import java.io.FileOutputStream

data class NaiveRAGUIState(
    val modelName: String = "未加载",
    val jsonFileName: String = "未选择JSON",
    val isModelReady: Boolean = false,
    val isLoading: Boolean = false,
    val isTesting: Boolean = false,
    val status: String = "",
    val progress: String = "",
    val avgTTFT: Double = 0.0,
    val avgSearchTime: Double = 0.0,
    val avgDecodeSpeed: Double = 0.0,
    val showResults: Boolean = false
)

sealed class NaiveRAGEvent {
    data class OnModelSelected(val uri: Uri, val context: Context) : NaiveRAGEvent()
    data class OnJsonSelected(val uri: Uri, val context: Context) : NaiveRAGEvent()
    object StartTest : NaiveRAGEvent()
}

@KoinViewModel
class NaiveRAGViewModel(
    private val chunksDB: ChunksDB,
    private val sentenceEncoder: SentenceEmbeddingProvider,
    private val llmManager: LLMManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(NaiveRAGUIState())
    val uiState: StateFlow<NaiveRAGUIState> = _uiState.asStateFlow()

    private var jsonQuestions: List<String> = emptyList()

    init {
        _uiState.update { it.copy(modelName = llmManager.modelName(), isModelReady = llmManager.isLoaded) }
    }

    fun onEvent(event: NaiveRAGEvent) {
        when (event) {
            is NaiveRAGEvent.OnModelSelected -> loadModel(event.uri, event.context)
            is NaiveRAGEvent.OnJsonSelected -> loadJson(event.uri, event.context)
            is NaiveRAGEvent.StartTest -> runTest()
        }
    }

    private fun loadModel(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, status = "加载模型...") }
            val fileName = getFileName(context, uri) ?: "model.gguf"
            val success = llmManager.loadModel(uri) { msg -> _uiState.update { it.copy(status = msg) } }
            _uiState.update { it.copy(isModelReady = success, modelName = llmManager.modelName(), isLoading = false) }
        }
    }

    private fun loadJson(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = getFileName(context, uri) ?: "questions.json"
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val arr = JSONArray(json)
                jsonQuestions = (0 until arr.length()).map { arr.getString(it) }
                _uiState.update { it.copy(jsonFileName = fileName) }
            } catch (e: Exception) { _uiState.update { it.copy(status = "JSON错误: ${e.message}") } }
        }
    }

    private fun runTest() {
        if (jsonQuestions.isEmpty() || !_uiState.value.isModelReady) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isTesting = true, showResults = false, progress = "开始测试...") }

            var totalSearch = 0L
            var totalTTFT = 0L
            var totalDecode = 0.0

            jsonQuestions.forEachIndexed { i, query ->
                _uiState.update { it.copy(progress = "处理 [${i + 1}/${jsonQuestions.size}]") }

                val t0 = System.currentTimeMillis()
                val emb = sentenceEncoder.encodeText(query)
                val chunks = chunksDB.getSimilarChunks(emb, 2).map { it.second }
                val context = chunks.joinToString(" ") { it.chunkData }
                val t1 = System.currentTimeMillis()
                totalSearch += (t1 - t0)

                val prompt = "Context: $context\nQuery: $query\nAnswer:"

                var ttft = 0L
                var tokens = 0
                val t2 = System.currentTimeMillis()
                var first = 0L

                try {
                    llmManager.generate(prompt) { token ->
                        if (first == 0L) { first = System.currentTimeMillis(); ttft = first - t2 }
                        tokens++
                    }
                } catch (e: Exception) {}

                val t3 = System.currentTimeMillis()
                totalTTFT += ttft
                totalDecode += if (t3 > first) tokens * 1000.0 / (t3 - first) else 0.0
            }

            val n = jsonQuestions.size
            _uiState.update { it.copy(isTesting = false, showResults = true, progress = "完成",
                avgSearchTime = totalSearch.toDouble() / n, avgTTFT = totalTTFT.toDouble() / n, avgDecodeSpeed = totalDecode / n) }
        }
    }

    private suspend fun copyUriToInternalCache(context: Context, uri: Uri, fileName: String): String? = withContext(Dispatchers.IO) {
        try { File(context.cacheDir, fileName).also { f -> context.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } } }.absolutePath } catch (e: Exception) { null }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") context.contentResolver.query(uri, null, null, null, null)?.use { if (it.moveToFirst()) result = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME)) }
        return result ?: uri.path?.let { File(it).name }
    }
}