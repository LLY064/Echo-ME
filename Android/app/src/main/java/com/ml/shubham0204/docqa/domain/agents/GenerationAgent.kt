package com.ml.shubham0204.docqa.domain.agents

import android.util.Log
import com.ml.shubham0204.docqa.data.ChunksDB
import com.ml.shubham0204.docqa.data.PFMRecord
import com.ml.shubham0204.docqa.data.Chunk
import com.ml.shubham0204.docqa.domain.SentenceEmbeddingProvider
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Single
class GenerationAgent(private val chunksDB: ChunksDB, private val encoder: SentenceEmbeddingProvider) {
    private val trajectories = ConcurrentHashMap<String, Trajectory>()
    private val counter = AtomicInteger(0)

    data class Trajectory(val query: String, val context: String, val response: String, val reward: Float, val time: Long)
    data class Result(val chunks: List<Chunk>, val scores: List<Float>, val time: Long)

    fun retrieve(query: String, dualMemory: List<Any> = emptyList()): Result {
        val start = System.currentTimeMillis()
        val queryEmb = encoder.encodeText(query)
        val candidates = chunksDB.getSimilarChunks(queryEmb, n = 20).map { (score, chunk) ->
            val boost = if (dualMemory.any { it is PFMRecord && chunk.chunkData.contains((it as PFMRecord).content.take(50)) }) 0.15f else 0f
            ScoreChunk((score + boost).coerceIn(0f, 1f), chunk)
        }.sortedByDescending { it.score }.take(10)

        return Result(candidates.map { it.chunk }, candidates.map { it.score }, System.currentTimeMillis() - start)
    }

    data class ScoreChunk(val score: Float, val chunk: Chunk)

    fun buildPrompt(query: String, chunks: List<Chunk>, memory: String): String {
        val context = chunks.joinToString("\n\n") { "[${it.docFileName}]\n${it.chunkData}" }
        val memSection = if (memory.isNotEmpty()) "\n\n--- 记忆 ---\n$memory" else ""
        return "你是一个智能助手。基于以下上下文信息回答问题。\n\n$context$memSection\n\n问题: $query\n回答:"
    }

    fun evaluate(query: String, response: String, context: String): Float {
        var reward = 0.3f
        if (response.isEmpty() || response.length < 5) reward -= 0.2f
        val overlap = context.split(" ").toSet().intersect(response.split(" ").toSet()).size.toFloat() / response.split(" ").size.coerceAtLeast(1)
        reward += (overlap * 0.4f).coerceIn(0f, 0.4f)
        if (response.contains("不知道") || response.contains("无法回答")) reward -= 0.1f
        return reward.coerceIn(-1f, 1f)
    }

    fun record(query: String, context: String, response: String, time: Long, reward: Float) {
        trajectories["traj_${counter.incrementAndGet()}"] = Trajectory(query, context, response, reward, time)
        Log.d("GenerationAgent", "Recorded trajectory, reward: $reward")
    }

    fun batchEvolve(): Map<String, Float> {
        val recent = trajectories.values.sortedByDescending { it.time }.take(20)
        if (recent.size < 2) return emptyMap()

        val groups = recent.groupBy { it.query.take(20) }
        return groups.mapNotNull { (_, trajs) ->
            if (trajs.size >= 2) {
                val best = trajs.maxByOrNull { it.reward }!!
                val worst = trajs.minByOrNull { it.reward }!!
                (best.query to (best.reward - worst.reward)).takeIf { it.second > 0.1f }
            } else null
        }.toMap()
    }

    fun topTrajectories(limit: Int = 5) = trajectories.values.sortedByDescending { it.reward }.take(limit)
}