package com.ml.shubham0204.docqa.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * 双记忆存储数据库 (SQLite)
 * - PFM 表: 事实记忆 (Plain Fact Memory)
 * - PEK 表: 经验知识 (Personal Experience Knowledge)
 */
class DualMemoryDB(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "dual_memory.db"
        private const val DATABASE_VERSION = 1
        private const val PFM_TABLE = "pfm_facts"
        private const val PEK_TABLE = "pek_experience"

        private const val PFM_CREATE = """
            CREATE TABLE IF NOT EXISTS $PFM_TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content TEXT NOT NULL,
                entity_type TEXT DEFAULT '',
                temporal_tag TEXT DEFAULT '',
                spatial_tag TEXT DEFAULT '',
                embedding BLOB,
                created_at INTEGER DEFAULT 0,
                last_accessed_at INTEGER DEFAULT 0,
                access_count INTEGER DEFAULT 0,
                importance_score REAL DEFAULT 0.5,
                is_anchored INTEGER DEFAULT 0
            )
        """

        private const val PEK_CREATE = """
            CREATE TABLE IF NOT EXISTS $PEK_TABLE (
                id TEXT PRIMARY KEY,
                content TEXT NOT NULL,
                category TEXT DEFAULT '',
                subtype TEXT DEFAULT '',
                confidence REAL DEFAULT 0.6,
                source TEXT DEFAULT 'generated',
                last_updated INTEGER DEFAULT 0,
                last_used INTEGER DEFAULT 0,
                evolution_stage INTEGER DEFAULT 0,
                reward_score REAL DEFAULT 0.0,
                trajectory_count INTEGER DEFAULT 0
            )
        """
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(PFM_CREATE)
        db.execSQL(PEK_CREATE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $PFM_TABLE")
        db.execSQL("DROP TABLE IF EXISTS $PEK_TABLE")
        onCreate(db)
    }

    fun insertPFM(record: PFMRecord): Long {
        val db = writableDatabase
        db.execSQL("""
            INSERT INTO $PFM_TABLE (content, entity_type, temporal_tag, spatial_tag, embedding, created_at, last_accessed_at, access_count, importance_score, is_anchored)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, arrayOf(
            record.content, record.entityType, record.temporalTag, record.spatialTag,
            record.embedding.toByteArray(), record.createdAt, record.lastAccessedAt,
            record.accessCount, record.importanceScore, if (record.isAnchored) 1 else 0
        ))
        return db.lastInsertRowId
    }

    fun queryAllPFM(): List<PFMRecord> {
        val results = mutableListOf<PFMRecord>()
        val db = readableDatabase
        val cursor = db.query(PFM_TABLE, null, null, null, null, null, "importance_score DESC")
        cursor.use { while (it.moveToNext()) results.add(cursorToPFM(it)) }
        return results
    }

    fun updatePFMAccess(id: Long) {
        writableDatabase.execSQL("UPDATE $PFM_TABLE SET last_accessed_at = ?, access_count = access_count + 1 WHERE id = ?",
            arrayOf(System.currentTimeMillis(), id))
    }

    fun deletePFM(id: Long) {
        writableDatabase.execSQL("DELETE FROM $PFM_TABLE WHERE id = ?", arrayOf(id))
    }

    fun insertOrUpdatePEK(entry: PEKEntry) {
        writableDatabase.execSQL("""
            INSERT OR REPLACE INTO $PEK_TABLE (id, content, category, subtype, confidence, source, last_updated, last_used, evolution_stage, reward_score, trajectory_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, arrayOf(
            entry.id, entry.content, entry.category, entry.subtype, entry.confidence,
            entry.source, entry.lastUpdated, entry.lastUsed, entry.evolutionStage,
            entry.rewardScore, entry.trajectoryCount
        ))
    }

    fun queryTopPEK(limit: Int): List<PEKEntry> {
        val results = mutableListOf<PEKEntry>()
        val db = readableDatabase
        val cursor = db.query(PEK_TABLE, null, null, null, null, null, "reward_score DESC", limit.toString())
        cursor.use { while (it.moveToNext()) results.add(cursorToPEK(it)) }
        return results
    }

    fun updatePEKEvolution(id: String, stage: Int, reward: Float) {
        writableDatabase.execSQL("UPDATE $PEK_TABLE SET evolution_stage = ?, reward_score = ?, trajectory_count = trajectory_count + 1 WHERE id = ?",
            arrayOf(stage, reward, id))
    }

    private fun cursorToPFM(cursor: Cursor): PFMRecord {
        val embBytes = cursor.getBlob(cursor.getColumnIndexOrThrow("embedding"))
        return PFMRecord(
            pfmId = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
            entityType = cursor.getString(cursor.getColumnIndexOrThrow("entity_type")),
            temporalTag = cursor.getString(cursor.getColumnIndexOrThrow("temporal_tag")),
            spatialTag = cursor.getString(cursor.getColumnIndexOrThrow("spatial_tag")),
            embedding = embBytes?.toFloatArray() ?: floatArrayOf(),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            lastAccessedAt = cursor.getLong(cursor.getColumnIndexOrThrow("last_accessed_at")),
            accessCount = cursor.getInt(cursor.getColumnIndexOrThrow("access_count")),
            importanceScore = cursor.getFloat(cursor.getColumnIndexOrThrow("importance_score")),
            isAnchored = cursor.getInt(cursor.getColumnIndexOrThrow("is_anchored")) == 1
        )
    }

    private fun cursorToPEK(cursor: Cursor): PEKEntry {
        return PEKEntry(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
            category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
            subtype = cursor.getString(cursor.getColumnIndexOrThrow("subtype")),
            confidence = cursor.getFloat(cursor.getColumnIndexOrThrow("confidence")),
            source = cursor.getString(cursor.getColumnIndexOrThrow("source")),
            lastUpdated = cursor.getLong(cursor.getColumnIndexOrThrow("last_updated")),
            lastUsed = cursor.getLong(cursor.getColumnIndexOrThrow("last_used")),
            evolutionStage = cursor.getInt(cursor.getColumnIndexOrThrow("evolution_stage")),
            rewardScore = cursor.getFloat(cursor.getColumnIndexOrThrow("reward_score")),
            trajectoryCount = cursor.getInt(cursor.getColumnIndexOrThrow("trajectory_count"))
        )
    }

    private fun FloatArray.toByteArray(): ByteArray {
        val result = ByteArray(size * 4)
        for (i in indices) {
            val bits = this[i].toRawBits()
            result[i * 4] = (bits and 0xFF).toByte()
            result[i * 4 + 1] = ((bits shr 8) and 0xFF).toByte()
            result[i * 4 + 2] = ((bits shr 16) and 0xFF).toByte()
            result[i * 4 + 3] = ((bits shr 24) and 0xFF).toByte()
        }
        return result
    }

    private fun ByteArray.toFloatArray(): FloatArray {
        val result = FloatArray(size / 4)
        for (i in result.indices) {
            val bits = (this[i * 4].toInt() and 0xFF) or
                    ((this[i * 4 + 1].toInt() and 0xFF) shl 8) or
                    ((this[i * 4 + 2].toInt() and 0xFF) shl 16) or
                    ((this[i * 4 + 3].toInt() and 0xFF) shl 24)
            result[i] = Float.fromBits(bits)
        }
        return result
    }
}