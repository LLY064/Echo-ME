package com.ml.shubham0204.docqa.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class PFMRecord( // 事实记忆 (Plain Fact Memory)
    @Id var pfmId: Long = 0,
    var content: String = "",
    var entityType: String = "",
    var temporalTag: String = "",
    var spatialTag: String = "",
    var embedding: FloatArray = floatArrayOf(),
    var createdAt: Long = 0,
    var lastAccessedAt: Long = 0,
    var accessCount: Int = 0,
    var importanceScore: Float = 0.5f,
    var isAnchored: Boolean = false
) {
    override fun equals(other: Any?) = other is PFMRecord && pfmId == other.pfmId
    override fun hashCode() = pfmId.hashCode()
}

data class PEKEntry( // 经验知识 (Personal Experience Knowledge)
    val id: String,
    val content: String,
    val category: String,
    val subtype: String,
    var confidence: Float,
    val source: String,
    val lastUpdated: Long,
    var lastUsed: Long,
    var evolutionStage: Int = 0,
    var rewardScore: Float = 0.0f,
    var trajectoryCount: Int = 0
)

data class DualMemoryState(
    val pfmCount: Int = 0,
    val pekCount: Int = 0,
    val l1HitRate: Float = 0.0f,
    val l2HitRate: Float = 0.0f,
    val memoryFragmentation: Float = 0.0f
)