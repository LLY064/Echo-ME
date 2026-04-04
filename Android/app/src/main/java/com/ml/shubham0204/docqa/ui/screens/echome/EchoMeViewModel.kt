package com.ml.shubham0204.docqa.ui.screens.echome

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ml.shubham0204.docqa.data.ChunksDB
import com.ml.shubham0204.docqa.data.DualMemoryManager
import com.ml.shubham0204.docqa.data.DualMemoryState
import com.ml.shubham0204.docqa.data.LLMManager
import com.ml.shubham0204.docqa.domain.agents.ChatMessage
import com.ml.shubham0204.docqa.domain.agents.GenerationAgent
import com.ml.shubham0204.docqa.domain.agents.MemoryAgent
import com.ml.shubham0204.docqa.domain.cache.HierarchicalCacheManager
import com.ml.shubham0204.docqa.domain.cache.MemoryMonitor
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

data class EchoMeUIState(
    val modelName: String = "未加载",
    val jsonFileName: String = "未选择JSON",
    val isModelReady: Boolean = false,
    val isLoading: Boolean = false,
    val isTesting: Boolean = false,
    val status: String = "",
    val progress: String = "",
    val avgSearch: Double = 0.0,
    val avgTTFT: Double = 0.0,
    val avgDecode: Double = 0.0,
    val avgFaults: Double = 0.0,
    val showResults: Boolean = false,
    val memoryState: DualMemoryState = DualMemoryState(),
    val cacheStats: HierarchicalCacheManager.Stats? = null,
    val memoryInfo: MemoryMonitor.MemoryInfo? = null,
    val topTrajectories: List<String> = emptyList()
)

sealed class EchoMeEvent {
    data class ModelSelected(val uri: Uri, val ctx: Context) : EchoMeEvent()
    data class JsonSelected(val uri: Uri, val ctx: Context) : EchoMeEvent()
    object StartTest : EchoMeEvent()
    object Refresh : EchoMeEvent()
}

@KoinViewModel
class EchoMeViewModel(
    private val chunksDB: ChunksDB,
    private val encoder: SentenceEmbeddingProvider,
    private val memManager: DualMemoryManager,
    private val memAgent: MemoryAgent,
    private val genAgent: GenerationAgent,
    private val cache: HierarchicalCacheManager,
    private val llmManager: LLMManager
) : ViewModel() {

    private val _ui = MutableStateFlow(EchoMeUIState())
    val ui: StateFlow<EchoMeUIState> = _ui.asStateFlow()

    private var queries: List<String> = emptyList()
    private val l1Set = mutableMapOf<Long, Long>()
    private val L1_CAP = 10
    private val PROMPT_LIMIT = 400

    init {
        _ui.update { it.copy(modelName = llmManager.modelName(), isModelReady = llmManager.isLoaded) }
    }

    fun onEvent(e: EchoMeEvent) {
        when (e) {
            is EchoMeEvent.ModelSelected -> loadModel(e.uri, e.ctx)
            is EchoMeEvent.JsonSelected -> loadJson(e.uri, e.ctx)
            is EchoMeEvent.StartTest -> runTest()
            is EchoMeEvent.Refresh -> refresh()
        }
    }

    private fun refresh() {
        _ui.update { it.copy(memoryState = memManager.getMemoryStats(), cacheStats = cache.stats(), memoryInfo = MemoryMonitor().info(),
            modelName = llmManager.modelName(), isModelReady = llmManager.isLoaded) }
    }

    private fun loadModel(uri: Uri, ctx: Context) {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, status = "加载模型...") }
            val success = llmManager.loadModel(uri) { msg -> _ui.update { it.copy(status = msg) } }
            _ui.update { it.copy(isModelReady = success, modelName = llmManager.modelName(), isLoading = false) }
            refresh()
        }
    }

    private fun loadJson(uri: Uri, ctx: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val name = getFileName(ctx, uri) ?: "questions.json"
                val json = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val arr = JSONArray(json)
                queries = (0 until arr.length()).map { arr.getString(it) }
                _ui.update { it.copy(jsonFileName = name) }
            } catch (e: Exception) { _ui.update { it.copy(status = "JSON错误: ${e.message}") } }
        }
    }

    private fun runTest() {
        if (queries.isEmpty() || !_ui.value.isModelReady) return
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(isTesting = true, showResults = false, progress = "多智能体协同推理...") }

            var totalSearch = 0L
            var totalTTFT = 0L
            var totalDecode = 0.0
            var totalFaults = 0

            queries.forEachIndexed { i, query ->
                _ui.update { it.copy(progress = "处理 [${i + 1}/${queries.size}]") }

                val t0 = System.currentTimeMillis()
                val emb = encoder.encodeText(query)
                val candidates = chunksDB.getSimilarChunks(emb, 50)

                val scored = candidates.map { (s, c) ->
                    val penalty = if (l1Set.containsKey(c.chunkId)) 0f else 0.15f
                    (s - penalty, c)
                }.sortedByDescending { it.first }

                val selected = mutableListOf<com.ml.shubham0204.docqa.data.Chunk>()
                var chars = 0
                var faults = 0

                for ((_, chunk) in scored) {
                    if (chars + chunk.chunkData.length > PROMPT_LIMIT) break
                    selected.add(chunk)
                    chars += chunk.chunkData.length
                    if (!l1Set.containsKey(chunk.chunkId)) {
                        faults++
                        if (l1Set.size >= L1_CAP) l1Set.entries.minByOrNull { it.value }?.key?.let { l1Set.remove(it) }
                    }
                    l1Set[chunk.chunkId] = System.currentTimeMillis()
                }

                totalFaults += faults
                val context = selected.joinToString(" ") { it.chunkData }
                val t1 = System.currentTimeMillis()
                totalSearch += (t1 - t0)

                val (facts, memCtx) = memAgent.retrieve(query)
                val reranked = genAgent.retrieve(query, facts)
                val prompt = genAgent.buildPrompt(query, reranked.chunks, memCtx)

                var ttft = 0L
                var tokens = 0
                val t2 = System.currentTimeMillis()
                var first = 0L

                try {
                    llmManager.generate(prompt) { token ->
                        if (first == 0L) { first = System.currentTimeMillis(); ttft = first - t2 }
                        tokens++
                    }
                } catch (e: Exception) { Log.w("EchoME", "截断", e) }

                val t3 = System.currentTimeMillis()
                totalTTFT += ttft
                totalDecode += if (t3 > first) tokens * 1000.0 / (t3 - first) else 0.0

                memAgent.store(memAgent.extract(listOf(ChatMessage("user", query), ChatMessage("assistant", "answer"))))
            }

            genAgent.batchEvolve().forEach { (q, r) -> memManager.addPEKExperience("优化: $q 改进 $r", "summary_policy", "evolution", r.coerceIn(0.3f, 0.9f), "auto") }

            val n = queries.size
            _ui.update {
                it.copy(isTesting = false, showResults = true, progress = "完成",
                    avgSearch = totalSearch.toDouble() / n, avgTTFT = totalTTFT.toDouble() / n,
                    avgDecode = totalDecode / n, avgFaults = totalFaults.toDouble() / n,
                    topTrajectories = genAgent.topTrajectories(3).map { "R:${String.format("%.2f", it.reward)} T:${it.time}ms" })
            }
            refresh()
        }
    }

    private fun getFileName(ctx: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") ctx.contentResolver.query(uri, null, null, null, null)?.use { if (it.moveToFirst()) name = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME)) }
        return name ?: uri.path?.let { File(it).name }
    }
}