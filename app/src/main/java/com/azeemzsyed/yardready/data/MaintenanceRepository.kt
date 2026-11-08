package com.azeemzsyed.yardready.data

import com.azeemzsyed.yardready.data.local.MaintenanceDao
import com.azeemzsyed.yardready.data.local.MaintenanceLog
import com.azeemzsyed.yardready.data.local.TaskStats
import com.azeemzsyed.yardready.data.local.TaskType
import com.azeemzsyed.yardready.data.sync.CloudSync
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class MaintenanceRepository(
    private val dao: MaintenanceDao,
    private val cloud: CloudSync,
) {
    fun history(locationId: Long): Flow<List<MaintenanceLog>> = dao.observeForLocation(locationId)

    fun lastCompleted(locationId: Long, type: TaskType): Flow<Long?> = dao.observeLastCompleted(locationId, type)

    fun stats(locationId: Long, since: Instant): Flow<List<TaskStats>> =
        dao.observeStats(locationId, since.toEpochMilli())

    suspend fun log(locationId: Long, type: TaskType, at: Instant, notes: String? = null): Long {
        val id = dao.insert(MaintenanceLog(locationId = locationId, taskType = type, completedAt = at.toEpochMilli(), notes = notes))
        syncPending() // best effort; the background worker retries later
        return id
    }

    suspend fun delete(log: MaintenanceLog) = dao.delete(log)

    /** Uploads unsynced rows to the cloud (no-op without Firebase). */
    suspend fun syncPending(): Int {
        if (!cloud.enabled) return 0
        val saved = cloud.upload(dao.unsynced())
        if (saved.isNotEmpty()) dao.markSynced(saved)
        return saved.size
    }
}
