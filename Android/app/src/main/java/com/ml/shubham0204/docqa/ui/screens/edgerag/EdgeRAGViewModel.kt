package com.ml.shubham0204.docqa.ui.screens.edgerag

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ml.shubham0204.docqa.data.Chunk
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
import kotlin.math.min
import kotlin.math.sqrt

data class EdgeRAGUIState(
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
    val showResults: Boolean = false,
    val indexStatus: String = "未建立"
)

sealed class EdgeRAGEvent {
    data class OnModelSelected(val uri: Uri, val context: Context) : EdgeRAGEvent()
    data class OnJsonSelected(val uri: Uri, val context: Context) : EdgeRAGEvent()
    object StartTest : EdgeRAGEvent()
}

@KoinViewModel
class EdgeRAGViewModel(
    private val chunksDB: ChunksDB,
    private val sentenceEncoder: SentenceEmbeddingProvider,
    private val llmManager: LLMManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(EdgeRAGUIState())
    val uiState: StateFlow<EdgeRAGUIState> = _uiState.asStateFlow()

    private var jsonQuestions: List<String> = emptyList()
    private var centroids = mutableMapOf<Int, FloatArray>()
    private var clusters = mutableMapOf<Int, List<Chunk>>()
    private var persistedVectors = mutableMapOf<Int, List<FloatArray>>()
    private var memoryCache = mutableMapOf<Int, List<FloatArray>>()

    init {
        _uiState.update { it.copy(modelName = llmManager.modelName(), isModelReady = llmManager.isLoaded) }
    }

    fun onEvent(event: EdgeRAGEvent) {
        when (event) {
            is EdgeRAGEvent.OnModelSelected -> loadModel(event.uri, event.context)
            is EdgeRAGEvent.OnJsonSelected -> loadJson(event.uri, event.context)
            is EdgeRAGEvent.StartTest -> runTest()
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
            } catch (e: Exception) {}
        }
    }

    private suspend fun buildEdgeIndex() {
        val allChunks = chunksDB.getAllChunks()
        if (allChunks.isEmpty()) return

        val SLO_THRESHOLD = 4
        val k = min(10, allChunks.size)

        _uiState.update { it.copy(indexStatus = "K-Means 聚类中...") }

        var currentCent = allChunks.shuffled().take(k).map { it.chunkEmbedding.clone() }.toMutableList()
        val tempClusters = mutableMapOf<Int, MutableList<Chunk>>()

        for (iter in 0 until 5) {
            tempClusters.clear()
            for (chunk in allChunks) {
                val bestK = currentCent.indices.maxByOrNull { i -> cosineSim(chunk.chunkEmbedding, currentCent[i]) } ?: 0
                tempClusters.getOrPut(bestK) { mutableListOf() }.add(chunk)
            }
            for (i in 0 until k) {
                val cChunks = tempClusters[i] ?: continue
                val newC = FloatArray(384)
                for (chunk in cChunks) for (d in 0 until 384) newC[d] += chunk.chunkEmbedding[d]
                for (d in 0 until 384) newC[d] /= cChunks.size.toFloat()
                currentCent[i] = newC
            }
        }

        centroids.clear(); clusters.clear(); persistedVectors.clear(); memoryCache.clear()

        for (i in 0 until k) {
            val cChunks = tempClusters[i] ?: continue
            if (cChunks.isEmpty()) continue
            centroids[i] = currentCent[i]
            clusters[i] = cChunks
            if (cChunks.size > SLO_THRESHOLD) persistedVectors[i] = cChunks.map { it.chunkEmbedding }
        }

        _uiState.update { it.copy(indexStatus = "完成: ${k}个聚类, 磁盘:${persistedVectors.size}") }
    }

    private fun runTest() {
        if (jsonQuestions.isEmpty() || !_uiState.value.isModelReady) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isTesting = true, showResults = false, progress = "构建索引...") }
            buildEdgeIndex()

            _uiState.update { it.copy(progress = "问答测试...") }

            var totalSearch = 0L
            var totalTTFT = 0L
            var totalDecode = 0.0

            jsonQuestions.forEachIndexed { i, query ->
                _uiState.update { it.copy(progress = "处理 [${i + 1}/${jsonQuestions.size}]") }

                val t0 = System.currentTimeMillis()
                val emb = sentenceEncoder.encodeText(query)
                val bestCluster = centroids.keys.maxByOrNull { cosineSim(emb, centroids[it]!!) } ?: -1
                val targetChunks = clusters[bestCluster] ?: emptyList()

                val vectors = when {
                    persistedVectors.containsKey(bestCluster) -> persistedVectors[bestCluster]!!
                    memoryCache.containsKey(bestCluster) -> memoryCache[bestCluster]!!
                    else -> {
                        val v = targetChunks.map { sentenceEncoder.encodeText(it.chunkData) }
                        memoryCache[bestCluster] = v
                        v
                    }
                }

                val topChunks = targetChunks.zip(vectors).map { it.first to cosineSim(emb, it.second) }
                    .sortedByDescending { it.second }.take(2).map { it.first }
                val context = topChunks.joinToString(" ") { it.chunkData }
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

    private fun cosineSim(v1: FloatArray, v2: FloatArray): Float {
        var dot = 0.0f; var normA = 0.0f; var normB = 0.0f
        for (i in v1.indices) { dot += v1[i] * v2[i]; normA += v1[i] * v1[i]; normB += v2[i] * v2[i] }
        return if (normA > 0 && normB > 0) dot / (sqrt(normA) * sqrt(normB)) else 0.0f
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") context.contentResolver.query(uri, null, null, null, null)?.use { if (it.moveToFirst()) result = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME)) }
        return result ?: uri.path?.let { File(it).name }
    }
}