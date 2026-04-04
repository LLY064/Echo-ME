package com.ml.shubham0204.docqa.domain.agents

import android.util.Log
import com.ml.shubham0204.docqa.data.DualMemoryManager
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

@Single
class MultiAgentOrchestrator(
    private val memoryAgent: MemoryAgent,
    private val generationAgent: GenerationAgent,
    private val dualMemoryManager: DualMemoryManager
) {
    private val sessions = ConcurrentHashMap<String, Session>()

    data class Session(val id: String, var queryCount: Int = 0, var totalReward: Float = 0f)
    data class Response(val text: String, val time: Long, val reward: Float)

    fun process(sessionId: String, query: String, generate: (String) -> String): Response {
        sessions.getOrPut(sessionId) { Session(sessionId) }.queryCount++

        val (facts, memory) = memoryAgent.retrieve(query)
        val extraction = memoryAgent.extract(listOf(ChatMessage("user", query)))
        memoryAgent.store(extraction)

        val reranked = generationAgent.retrieve(query, facts)
        val prompt = generationAgent.buildPrompt(query, reranked.chunks, memory)

        val start = System.currentTimeMillis()
        val response = generate(prompt)
        val time = System.currentTimeMillis() - start
        val reward = generationAgent.evaluate(query, response, reranked.chunks.joinToString { it.chunkData })

        sessions[sessionId]?.totalReward += reward
        generationAgent.record(query, memory, response, time, reward)

        if (sessions[sessionId]?.queryCount!! % 5 == 0) {
            generationAgent.batchEvolve().forEach { (q, imp) ->
                dualMemoryManager.addPEKExperience("基于反馈优化: $q 改进 $imp", "summary_policy", "evolution", imp.coerceIn(0.3f, 0.9f), "auto")
            }
        }

        Log.d("Orchestrator", "Processed in ${time}ms, reward: $reward")
        return Response(response, time, reward)
    }

    fun state() = sessions.values.sumOf { it.queryCount } to sessions.values.map { it.totalReward }.average().toFloat()
}