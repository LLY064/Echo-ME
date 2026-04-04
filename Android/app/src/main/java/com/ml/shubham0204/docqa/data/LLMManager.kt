package com.ml.shubham0204.docqa.data

import android.content.Context
import android.net.Uri
import android.util.Log
import io.shubham0204.smollm.SmolLM
import org.koin.core.annotation.Single
import java.io.File
import java.io.FileOutputStream

@Single
class LLMManager(private val context: Context) {
    private var smollm: SmolLM? = null
    private var currentModelPath: String? = null
    var isLoaded: Boolean = false
        private set

    fun loadModel(uri: Uri, onProgress: (String) -> Unit): Boolean {
        return try {
            onProgress("复制模型文件...")
            val fileName = getFileName(uri) ?: "model.gguf"
            val path = copyToCache(uri, fileName)
            if (path == null) {
                onProgress("文件复制失败")
                return false
            }

            onProgress("加载模型中...")
            smollm?.close()
            smollm = SmolLM().apply {
                load(path, SmolLM.InferenceParams(contextSize = 4096, storeChats = false))
                addSystemPrompt("你是一个智能助手。")
            }
            currentModelPath = path
            isLoaded = true
            onProgress("模型加载完成: $fileName")
            Log.d("LLMManager", "Model loaded: $fileName")
            true
        } catch (e: Exception) {
            Log.e("LLMManager", "Load failed: ${e.message}")
            onProgress("加载失败: ${e.message}")
            false
        }
    }

    fun generate(prompt: String, onToken: (String) -> Unit): String {
        val llm = smollm ?: throw Exception("模型未加载")
        var fullResponse = ""
        llm.addUserMessage(prompt)
        try {
            llm.getResponseAsFlow(prompt).collect { token ->
                fullResponse += token
                onToken(token)
            }
        } catch (e: Exception) {
            Log.w("LLMManager", "Generation truncated: ${e.message}")
        }
        return fullResponse
    }

    fun generateSync(prompt: String): String {
        val llm = smollm ?: throw Exception("模型未加载")
        var response = ""
        llm.addUserMessage(prompt)
        try {
            llm.getResponseAsFlow(prompt).collect { response += it }
        } catch (e: Exception) { Log.w("LLMManager", "Sync truncated") }
        return response
    }

    fun close() {
        smollm?.close()
        smollm = null
        currentModelPath = null
        isLoaded = false
    }

    fun modelName(): String = currentModelPath?.let { File(it).name } ?: "未加载"

    private fun copyToCache(uri: Uri, name: String): String? {
        return try {
            File(context.cacheDir, name).also { f ->
                context.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            }.absolutePath
        } catch (e: Exception) { null }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME))
        }
        return name ?: uri.path?.let { File(it).name }
    }
}