package com.notiforge.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    @Query("SELECT * FROM templates ORDER BY isPreset DESC, updatedAtEpochMillis DESC")
    fun observeAllTemplates(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates ORDER BY isPreset DESC, updatedAtEpochMillis DESC")
    suspend fun getAllTemplates(): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TemplateEntity?

    @Query("SELECT * FROM templates WHERE slug = :slug LIMIT 1")
    suspend fun getBySlug(slug: String): TemplateEntity?

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: TemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(entities: List<TemplateEntity>)

    @Update
    suspend fun update(entity: TemplateEntity)

    @Delete
    suspend fun delete(entity: TemplateEntity)
}

@Dao
interface EventLogDao {

    @Query("SELECT * FROM event_logs ORDER BY timestampMillis DESC LIMIT 20")
    fun observeRecentLogs(): Flow<List<EventLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: EventLogEntity)

    @Query("DELETE FROM event_logs WHERE id NOT IN (SELECT id FROM event_logs ORDER BY timestampMillis DESC LIMIT 20)")
    suspend fun pruneOldLogs()

    @Query("DELETE FROM event_logs")
    suspend fun clearAll()
}
