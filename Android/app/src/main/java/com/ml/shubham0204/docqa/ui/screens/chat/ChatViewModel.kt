package com.ml.shubham0204.docqa.ui.screens.chat

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ml.shubham0204.docqa.data.ChunksDB
import com.ml.shubham0204.docqa.data.DocumentsDB
import com.ml.shubham0204.docqa.data.LLMManager
import com.ml.shubham0204.docqa.data.RetrievedContext
import com.ml.shubham0204.docqa.domain.SentenceEmbeddingProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

sealed class ChatScreenUIEvent {
    data object OnOpenDocsClick : ChatScreenUIEvent()
    data object OnNaiveRAGClick : ChatScreenUIEvent()
    data object OnEdgeRAGClick : ChatScreenUIEvent()
    data object OnEchoMeClick : ChatScreenUIEvent()

    sealed class ResponseGeneration {
        data class Start(val query: String, val prompt: String) : ChatScreenUIEvent()
        data class StopWithSuccess(val response: String, val contexts: List<RetrievedContext>) : ChatScreenUIEvent()
        data class StopWithError(val error: String) : ChatScreenUIEvent()
    }
}

sealed class ChatNavEvent {
    data object None : ChatNavEvent()
    data object ToDocsScreen : ChatNavEvent()
    data object ToNaiveRAGScreen : ChatNavEvent()
    data object ToEdgeRAGScreen : ChatNavEvent()
    data object ToEchoMeScreen : ChatNavEvent()
}

data class ChatScreenUIState(
    val question: String = "",
    val response: String = "",
    val isGenerating: Boolean = false,
    val retrievedContexts: List<RetrievedContext> = emptyList(),
)

@KoinViewModel
class ChatViewModel(
    private val context: Context,
    private val docsDB: DocumentsDB,
    private val chunksDB: ChunksDB,
    private val encoder: SentenceEmbeddingProvider,
    private val llmManager: LLMManager
) : ViewModel() {

    private val _ui = MutableStateFlow(ChatScreenUIState())
    val chatScreenUIState: StateFlow<ChatScreenUIState> = _ui

    private val _navChannel = Channel<ChatNavEvent>()
    val navEventChannel = _navChannel.receiveAsFlow()

    fun onChatScreenEvent(event: ChatScreenUIEvent) {
        when (event) {
            is ChatScreenUIEvent.ResponseGeneration.Start -> {
                if (docsDB.getDocsCount() == 0) {
                    Toast.makeText(context, "请先添加文档", Toast.LENGTH_LONG).show()
                    return
                }
                if (!llmManager.isLoaded) {
                    Toast.makeText(context, "请先在 Echo-ME 界面加载模型", Toast.LENGTH_LONG).show()
                    return
                }
                if (event.query.isBlank()) {
                    Toast.makeText(context, "请输入问题", Toast.LENGTH_LONG).show()
                    return
                }

                _ui.value = _ui.value.copy(isGenerating = true, question = event.query)
                processQuery(event.query, event.prompt)
            }

            is ChatScreenUIEvent.ResponseGeneration.StopWithSuccess -> {
                _ui.value = _ui.value.copy(isGenerating = false, response = event.response, retrievedContexts = event.contexts)
            }

            is ChatScreenUIEvent.ResponseGeneration.StopWithError -> {
                _ui.value = _ui.value.copy(isGenerating = false, question = "")
            }

            is ChatScreenUIEvent.OnOpenDocsClick -> viewModelScope.launch { _navChannel.send(ChatNavEvent.ToDocsScreen) }
            is ChatScreenUIEvent.OnNaiveRAGClick -> viewModelScope.launch { _navChannel.send(ChatNavEvent.ToNaiveRAGScreen) }
            is ChatScreenUIEvent.OnEdgeRAGClick -> viewModelScope.launch { _navChannel.send(ChatNavEvent.ToEdgeRAGScreen) }
            is ChatScreenUIEvent.OnEchoMeClick -> viewModelScope.launch { _navChannel.send(ChatNavEvent.ToEchoMeScreen) }
        }
    }

    private fun processQuery(query: String, prompt: String) {
        try {
            val contexts = mutableListOf<RetrievedContext>()
            val emb = encoder.encodeText(query)
            chunksDB.getSimilarChunks(emb, 5).forEach { (_, chunk) ->
                contexts.add(RetrievedContext(chunk.docFileName, chunk.chunkData))
            }

            val fullPrompt = prompt.replace("\$CONTEXT", contexts.joinToString("\n") { it.context })
                .replace("\$QUERY", query)

            val response = llmManager.generateSync(fullPrompt)
            onChatScreenEvent(ChatScreenUIEvent.ResponseGeneration.StopWithSuccess(response, contexts))
        } catch (e: Exception) {
            onChatScreenEvent(ChatScreenUIEvent.ResponseGeneration.StopWithError(e.message ?: "错误"))
        }
    }
}