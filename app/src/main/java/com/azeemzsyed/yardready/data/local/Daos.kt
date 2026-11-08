package com.azeemzsyed.yardready.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LocationDao {

    @Query("SELECT * FROM locations WHERE is_selected = 1 LIMIT 1")
    abstract fun observeSelected(): Flow<SavedLocation?>

    @Query("SELECT * FROM locations WHERE is_selected = 1 LIMIT 1")
    abstract suspend fun getSelected(): SavedLocation?

    @Query("SELECT * FROM locations ORDER BY name")
    abstract fun observeAll(): Flow<List<SavedLocation>>

    @Query(
        """SELECT * FROM locations
           WHERE ABS(latitude - :lat) < 0.01 AND ABS(longitude - :lon) < 0.01
           LIMIT 1"""
    )
    abstract suspend fun findNear(lat: Double, lon: Double): SavedLocation?

    @Insert
    abstract suspend fun insert(location: SavedLocation): Long

    /** Marks exactly one location as selected (SQLite evaluates id = :id to 0 or 1). */
    @Query("UPDATE locations SET is_selected = (id = :id)")
    abstract suspend fun select(id: Long)

    @Delete
    abstract suspend fun delete(location: SavedLocation)

    /** Re-uses an existing row for the same place instead of duplicating it. */
    @Transaction
    open suspend fun saveAndSelect(location: SavedLocation): Long {
        val id = findNear(location.latitude, location.longitude)?.id ?: insert(location)
        select(id)
        return id
    }
}

@Dao
interface MaintenanceDao {

    @Insert
    suspend fun insert(log: MaintenanceLog): Long

    @Delete
    suspend fun delete(log: MaintenanceLog)

    @Query("SELECT * FROM maintenance_logs WHERE location_id = :locationId ORDER BY completed_at DESC")
    fun observeForLocation(locationId: Long): Flow<List<MaintenanceLog>>

    @Query(
        """SELECT MAX(completed_at) FROM maintenance_logs
           WHERE location_id = :locationId AND task_type = :type"""
    )
    fun observeLastCompleted(locationId: Long, type: TaskType): Flow<Long?>

    @Query(
        """SELECT MAX(completed_at) FROM maintenance_logs
           WHERE location_id = :locationId AND task_type = :type"""
    )
    suspend fun lastCompleted(locationId: Long, type: TaskType): Long?

    /** How many times each task was done since [since], and when it was last done. */
    @Query(
        """SELECT task_type AS taskType,
                  COUNT(*) AS count,
                  MAX(completed_at) AS lastCompletedAt
           FROM maintenance_logs
           WHERE location_id = :locationId AND completed_at >= :since
           GROUP BY task_type
           ORDER BY task_type"""
    )
    fun observeStats(locationId: Long, since: Long): Flow<List<TaskStats>>

    @Query("SELECT * FROM maintenance_logs WHERE synced = 0")
    suspend fun unsynced(): List<MaintenanceLog>

    @Query("UPDATE maintenance_logs SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}

@Dao
interface SnowAlertDao {

    /** Returns -1 if this storm was already recorded (unique location + start). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: SnowAlertRecord): Long

    @Query("SELECT * FROM snow_alerts WHERE location_id = :locationId ORDER BY event_start DESC LIMIT :limit")
    suspend fun recent(locationId: Long, limit: Int = 20): List<SnowAlertRecord>
}
