package com.ml.shubham0204.docqa.domain.cache

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.math.min

class HierarchicalCacheManager(private val context: Context) {
    private val L1_MAX = 16 * 256
    private val L2_SIZE = 128 * 1024 * 1024
    private val PAGE_SIZE = 4096

    private val l1 = ConcurrentHashMap<String, ByteArray>()
    private val l2Idx = ConcurrentHashMap<String, L2Meta>()
    private val prefetchQ = PriorityBlockingQueue<PrefetchTask>(100) { a, b -> b.priority - a.priority }
    private val accessHist = ConcurrentHashMap<String, List<String>>()
    private val lock = ReentrantReadWriteLock()

    private val exec = Executors.newFixedThreadPool(2)
    private val worker = HandlerThread("PrefetchWorker").also { it.start() }
    private val handler = Handler(worker.looper)
    private val running = AtomicBoolean(true)

    data class L2Meta(val offset: Long, val size: Int, var lastAccess: Long)
    data class PrefetchTask(val key: String, val priority: Int, val ts: Long = System.currentTimeMillis())
    data class Stats(val l1Hit: Float, val l2Hit: Float, val evictions: Int, val prefetched: Int)

    private var l2File: RandomAccessFile? = null
    private var l2Buf: MappedByteBuffer? = null
    private var l1Hits = 0
    private var l1Miss = 0
    private var l2Hits = 0
    private var l2Miss = 0
    private var evicts = 0
    private var prefetches = 0

    init {
        try {
            val f = context.filesDir.resolve("l2_cache.dat")
            l2File = RandomAccessFile(f, "rw")
            l2Buf = l2File?.channel?.map(FileChannel.MapMode.READ_WRITE, 0, L2_SIZE.toLong())
            Log.d("Cache", "L2 initialized")
        } catch (e: Exception) { Log.e("Cache", "L2 init failed: ${e.message}") }
        exec.submit { while (running.get()) { prefetchQ.poll()?.let { if (System.currentTimeMillis() - it.ts < 30000) prefetch(it.key) } } }
    }

    fun prefetch(keys: List<String>, current: String) {
        keys.forEach { prefetchQ.offer(PrefetchTask(it, if (it == current) 10 else 5)) }
        handler.postDelayed({ accessHist[current]?.lastOrNull()?.let { prefetchQ.offer(PrefetchTask(it, 3)) } }, 100)
    }

    fun get(key: String): ByteArray? {
        l1[key]?.let { l1Hits++; updateHist(key); return it }
        l1Miss++
        l2Idx[key]?.let { meta ->
            l2Hits++
            l2Buf?.let { buf ->
                val data = ByteArray(meta.size)
                buf.position(meta.offset.toInt())
                buf.get(data, 0, meta.size)
                meta.lastAccess = System.currentTimeMillis()
                putL1(key, data)
                return data
            }
        }
        l2Miss++
        return null
    }

    fun put(key: String, data: ByteArray) {
        putL1(key, data)
        putL2(key, data)
        updateHist(key)
    }

    private fun putL1(key: String, data: ByteArray) {
        lock.write {
            if (l1.size >= L1_MAX) {
                l1.entries.minByOrNull { it.value.size }?.key?.let { l1.remove(it); evicts++ }
            }
            l1[key] = data
        }
    }

    private fun putL2(key: String, data: ByteArray) {
        lock.write {
            val size = l2Idx.values.sumOf { it.size }
            if (size + data.size > L2_SIZE) {
                l2Idx.values.sortedBy { it.lastAccess }.take(l2Idx.size / 4).forEach { l2Idx.remove(it.key); evicts++ }
            }
            l2Buf?.let { buf ->
                val offset = (l2Idx.size * PAGE_SIZE).toLong()
                buf.position(offset.toInt())
                buf.put(data)
                l2Idx[key] = L2Meta(offset, data.size, System.currentTimeMillis())
            }
        }
    }

    private fun prefetch(key: String) {
        if (l1.containsKey(key)) return
        l2Idx[key]?.let { meta ->
            l2Buf?.let { buf ->
                val data = ByteArray(meta.size)
                buf.position(meta.offset.toInt())
                buf.get(data, 0, meta.size)
                putL1(key, data)
                prefetches++
            }
        }
    }

    private fun updateHist(key: String) {
        val prev = accessHist[key] ?: emptyList()
        accessHist[key] = (prev + key).takeLast(5)
    }

    fun stats(): Stats {
        val total = l1Hits + l1Miss
        return Stats(if (total > 0) l1Hits.toFloat() / total else 0f,
            if (l2Miss > 0) l2Hits.toFloat() / (l2Hits + l2Miss) else 0f, evicts, prefetches)
    }

    fun shutdown() {
        running.set(false)
        l2Buf?.force()
        l2File?.close()
        worker.quitSafely()
        exec.shutdown()
    }
}

class MemoryMonitor {
    private val Runtime.r: Runtime get() = Runtime.getRuntime()
    fun info() = r.let {
        val max = it.maxMemory() / 1024 / 1024
        val used = (it.totalMemory() - it.freeMemory()) / 1024 / 1024
        MemoryInfo(max, used, used.toFloat() / max)
    }
    data class MemoryInfo(val maxMb: Long, val usedMb: Long, val ratio: Float)
}