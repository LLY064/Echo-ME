package com.ml.shubham0204.docqa.domain.agents

import com.ml.shubham0204.docqa.data.DualMemoryManager
import com.ml.shubham0204.docqa.data.PFMRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

@Single
class MemoryAgent(private val dualMemoryManager: DualMemoryManager) {
    private val rules = ConcurrentHashMap<String, Rule>()

    data class Rule(val entityType: String, val temporalPattern: Regex, val importance: Float)
    data class Extraction(val facts: List<PFMRecord>, val policies: List<String>, val quality: Float)

    init {
        rules["person"] = Rule("person", Regex("\\d{4}年|上周|昨天|今天|现在"), 0.9f)
        rules["location"] = Rule("location", Regex("位于|在|市|省|区|县"), 0.8f)
        rules["event"] = Rule("event", Regex("\\d+月\\d+日|周\\d"), 0.85f)
        rules["preference"] = Rule("preference", Regex("喜欢|讨厌|偏好|经常"), 0.7f)
    }

    suspend fun extract(messages: List<ChatMessage>): Extraction = withContext(Dispatchers.Default) {
        val facts = mutableListOf<PFMRecord>()
        val policies = mutableListOf<String>()
        var quality = 0f
        var count = 0

        messages.forEach { msg ->
            if (msg.role == "user") {
                rules.forEach { (_, rule) ->
                    if (rule.temporalPattern.containsMatchIn(msg.content)) {
                        facts.add(PFMRecord(content = msg.content.take(200), entityType = rule.entityType,
                            importanceScore = (rule.importance * 0.7f + min(1f, msg.content.length / 500f) * 0.3f).coerceIn(0.1f, 1f)))
                        quality += 0.8f; count++
                    }
                }
                if (msg.content.contains("喜欢")) policies.add("记住用户喜欢的事物")
                if (msg.content.contains("不要") || msg.content.contains("避免")) policies.add("避免: ${msg.content.substringAfter("不要").substringBefore("。")}")
            } else if (msg.role == "assistant") {
                if (msg.content.contains("抱歉")) policies.add("保持礼貌道歉风格")
                if (msg.content.contains("建议")) policies.add("提供建议风格")
                quality += 0.7f; count++
            }
        }
        Extraction(facts, policies, if (count > 0) quality / count else 0.5f)
    }

    fun store(extraction: Extraction) {
        extraction.facts.forEach { dualMemoryManager.addPFMFact(it.content, it.entityType, importance = it.importanceScore) }
        extraction.policies.forEach { dualMemoryManager.addPEKExperience(it, "summary_policy", "extracted", extraction.quality) }
    }

    fun retrieve(query: String): Pair<List<PFMRecord>, String> {
        val facts = dualMemoryManager.retrievePFMFacts(query, 5).map { it.first }
        val policies = dualMemoryManager.getRelevantKnowledges(query)
        return facts to policies
    }
}

data class ChatMessage(val role: String, val content: String, val timestamp: Long = System.currentTimeMillis())