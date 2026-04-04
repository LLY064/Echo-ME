package com.ml.shubham0204.docqa.data

import org.koin.core.annotation.Single

@Single
class ChunksDB {
    private val chunksBox = ObjectBoxStore.store.boxFor(Chunk::class.java)

    fun addChunk(chunk: Chunk) {
        chunksBox.put(chunk)
    }

    fun getAllChunks(): List<Chunk> {
        return chunksBox.all
    }

//    fun getSimilarChunks(
//        queryEmbedding: FloatArray,
//        n: Int = 5,
//    ): List<Pair<Float, Chunk>> {
//        /*
//        Use maxResultCount to set the maximum number of objects to return by the ANN condition.
//        Hint: it can also be used as the "ef" HNSW parameter to increase the search quality in combination
//        with a query limit. For example, use maxResultCount of 100 with a Query limit of 10 to have 10 results
//        that are of potentially better quality than just passing in 10 for maxResultCount
//        (quality/performance tradeoff).
//         */
//        return chunksBox
//            .query(Chunk_.chunkEmbedding.nearestNeighbors(queryEmbedding, 25))
//            .build()
//            .findWithScores()
//            .map { Pair(it.score.toFloat(), it.get()) }
//            .subList(0, n)
//    }

    fun getSimilarChunks(
        queryEmbedding: FloatArray,
        n: Int = 5,
    ): List<Pair<Float, Chunk>> {
        return chunksBox
            // 将硬编码的 25 改为 n，或者为了搜索质量可以使用 n * 2 等策略
            .query(Chunk_.chunkEmbedding.nearestNeighbors(queryEmbedding, n))
            .build()
            .findWithScores()
            .map { Pair(it.score.toFloat(), it.get()) }
            .take(n) // 替换掉原来的 .subList(0, n)
    }

    fun removeChunks(docId: Long) {
        chunksBox.removeByIds(
            chunksBox
                .query(Chunk_.docId.equal(docId))
                .build()
                .findIds()
                .toList(),
        )
    }
}
