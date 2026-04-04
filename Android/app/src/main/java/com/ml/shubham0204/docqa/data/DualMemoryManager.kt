package com.ml.shubham0204.docqa.data

import android.content.Context
import android.util.Log
import com.ml.shubham0204.docqa.domain.SentenceEmbeddingProvider
import org.koin.core.annotation.Single
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.math.ln
import kotlin.math.sqrt

@Single
class DualMemoryManager(
    private val context: Context,
    private val embeddingProvider: SentenceEmbeddingProvider
) {
    private val db = DualMemoryDB(context)
    private val executor = Executors.newFixedThreadPool(2)
    private val lock = ReentrantReadWriteLock()
    private val pfmCache = ConcurrentHashMap<Long, PFMRecord>()
    private val pekCache = ConcurrentHashMap<String, PEKEntry>()
    private val embeddingIndex = ConcurrentHashMap<Long, FloatArray>()

    private val PFM_CAPACITY = 100
    private val PEK_CAPACITY = 50

    init { loadFromDB() }

    private fun loadFromDB() {
        executor.submit {
            try {
                db.queryAllPFM().forEach { pfm ->
                    pfmCache[pfm.pfmId] = pfm
                    if (pfm.embedding.isNotEmpty()) embeddingIndex[pfm.pfmId] = pfm.embedding
                }
                db.queryTopPEK(PEK_CAPACITY).forEach { pekCache[it.id] = it }
                Log.d("DualMemoryManager", "Loaded PFM: ${pfmCache.size}, PEK: ${pekCache.size}")
            } catch (e: Exception) { Log.e("DualMemoryManager", "Load error: ${e.message}") }
        }
    }

    fun addPFMFact(content: String, entityType: String = "general", temporalTag: String = "", spatialTag: String = "", importance: Float = 0.5f): Long {
        val embedding = embeddingProvider.encodeText(content)
        val now = System.currentTimeMillis()
        val record = PFMRecord(content = content, entityType = entityType, temporalTag = temporalTag, spatialTag = spatialTag,
            embedding = embedding, createdAt = now, lastAccessedAt = now, importanceScore = importance)
        lock.write {
            if (pfmCache.size >= PFM_CAPACITY) pfmCache.values.sortedBy { it.importanceScore }.take(pfmCache.size / 4).forEach {
                pfmCache.remove(it.pfmId); embeddingIndex.remove(it.pfmId); db.deletePFM(it.pfmId)
            }
            val id = db.insertPFM(record)
            record.pfmId = id
            pfmCache[id] = record
            embeddingIndex[id] = embedding
        }
        return record.pfmId
    }

    fun retrievePFMFacts(query: String, topK: Int = 5, minScore: Float = 0.5f): List<Pair<PFMRecord, Float>> {
        val queryEmb = embeddingProvider.encodeText(query)
        val candidates = lock.read {
            pfmCache.values.mapNotNull { pfm ->
                embeddingIndex[pfm.pfmId]?.let { emb ->
                    val score = cosineSimilarity(queryEmb, emb)
                    if (score >= minScore) pfm to (score * pfm.importanceScore * (1.0f + 0.1f * ln(pfm.accessCount + 1.0f).toFloat())) else null
                }
            }.sortedByDescending { it.second }.take(topK)
        }
        candidates.forEach { (pfm, _) ->
            db.updatePFMAccess(pfm.pfmId)
            pfm.apply { accessCount++; lastAccessedAt = System.currentTimeMillis() }
        }
        return candidates
    }

    fun addPEKExperience(content: String, category: String, subtype: String = "general", confidence: Float = 0.6f, source: String = "generated"): String {
        val candidateEmb = embeddingProvider.encodeText(content)
        lock.read {
            pekCache.values.find { it.category == category && cosineSimilarity(candidateEmb, embeddingProvider.encodeText(it.content)) > 0.85f }?.let {
                it.confidence = (it.confidence + 0.02f).coerceAtMost(1.0f)
                return it.id
            }
        }
        val newId = "pek_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val entry = PEKEntry(id = newId, content = content, category = category, subtype = subtype, confidence = confidence,
            source = source, lastUpdated = now, lastUsed = 0, evolutionStage = 0, rewardScore = 0.0f, trajectoryCount = 0)
        lock.write {
            if (pekCache.size >= PEK_CAPACITY) pekCache.values.sortedBy { it.rewardScore }.take(pekCache.size / 4).forEach { pekCache.remove(it.id) }
            pekCache[newId] = entry
            db.insertOrUpdatePEK(entry)
        }
        return newId
    }

    fun getSummaryPolicies(): String = lock.read {
        pekCache.values.filter { it.category == "summary_policy" }.sortedByDescending { it.rewardScore }.take(3).joinToString("\n") { "- ${it.content}" }
    }.ifEmpty { "None." }

    fun getRelevantKnowledges(query: String, topK: Int = 3): String {
        val queryEmb = embeddingProvider.encodeText(query)
        return lock.read {
            pekCache.values.filter { it.category == "user_knowledge" }.mapNotNull { entry ->
                embeddingProvider.encodeText(entry.content).let { emb ->
                    cosineSimilarity(queryEmb, emb).takeIf { it >= 0.6f }?.let { score -> entry to score }
                }
            }.sortedByDescending { it.second }.take(topK).joinToString("\n") { "- ${it.first.content}" }
        }
    }

    fun evolvePEK(entryId: String, reward: Float) {
        lock.write {
            pekCache[entryId]?.let { entry ->
                entry.trajectoryCount++
                val oldStage = entry.evolutionStage
                entry.rewardScore = entry.rewardScore * 0.7f + reward * 0.3f
                if (reward - entry.rewardScore > -0.05f && entry.trajectoryCount >= 3) entry.evolutionStage = (oldStage + 1).coerceAtMost(3)
                db.updatePEKEvolution(entryId, entry.evolutionStage, entry.rewardScore)
            }
        }
    }

    fun batchEvolvePEK(trajectories: List<Pair<String, Float>>) = trajectories.forEach { (id, reward) -> evolvePEK(id, reward) }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dot = 0.0f; var normA = 0.0f; var normB = 0.0f
        for (i in v1.indices) { dot += v1[i] * v2[i]; normA += v1[i] * v1[i]; normB += v2[i] * v2[i] }
        return if (normA > 0 && normB > 0) dot / (sqrt(normA) * sqrt(normB)) else 0.0f
    }

    fun getMemoryStats(): DualMemoryState = lock.read {
        val recent = pfmCache.values.count { System.currentTimeMillis() - it.lastAccessedAt < 60000 }
        DualMemoryState(pfmCache.size, pekCache.size, if (pfmCache.isEmpty()) 0f else recent.toFloat() / pfmCache.size, 0f, 0f)
    }

    fun shutdown() = executor.shutdown()
}