package com.notiforge.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores recent incoming and outgoing IPC events (last 20 events) for in-app inspection & debugging.
 */
@Entity(tableName = "event_logs")
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestampMillis: Long = System.currentTimeMillis(),
    val direction: String, // "INCOMING" or "OUTGOING"
    val action: String,
    val notificationTag: String,
    val summary: String
)
