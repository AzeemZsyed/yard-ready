package com.azeemzsyed.yardready.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Relational schema (SQLite via Room):
 *
 *   locations (1) ──< maintenance_logs   every logged mow / snow clearing belongs to a yard
 *   locations (1) ──< snow_alerts        remembers which storms we already notified about
 *
 * Deleting a location cascades to its logs and alerts.
 */

enum class TaskType { MOW, CLEAR_SNOW }

@Entity(tableName = "locations")
data class SavedLocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val region: String? = null,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "is_selected") val isSelected: Boolean = false,
)

@Entity(
    tableName = "maintenance_logs",
    foreignKeys = [
        ForeignKey(
            entity = SavedLocation::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("location_id"), Index(value = ["location_id", "task_type", "completed_at"])],
)
data class MaintenanceLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "location_id") val locationId: Long,
    @ColumnInfo(name = "task_type") val taskType: TaskType,
    /** Epoch milliseconds */
    @ColumnInfo(name = "completed_at") val completedAt: Long,
    val notes: String? = null,
    /** Whether this row has been backed up to Firebase */
    val synced: Boolean = false,
)

@Entity(
    tableName = "snow_alerts",
    foreignKeys = [
        ForeignKey(
            entity = SavedLocation::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["location_id", "event_start"], unique = true)],
)
data class SnowAlertRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "location_id") val locationId: Long,
    /** Epoch milliseconds of the storm start, so each storm is only notified once */
    @ColumnInfo(name = "event_start") val eventStart: Long,
    @ColumnInfo(name = "expected_cm") val expectedCm: Double,
    @ColumnInfo(name = "notified_at") val notifiedAt: Long,
)

/** Result row for the per-task statistics query. */
data class TaskStats(
    val taskType: TaskType,
    val count: Int,
    val lastCompletedAt: Long,
)
