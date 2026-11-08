package com.azeemzsyed.yardready

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azeemzsyed.yardready.data.local.AppDatabase
import com.azeemzsyed.yardready.data.local.MaintenanceLog
import com.azeemzsyed.yardready.data.local.SavedLocation
import com.azeemzsyed.yardready.data.local.SnowAlertRecord
import com.azeemzsyed.yardready.data.local.TaskType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the SQL queries and relational constraints against a real (in-memory) SQLite database. */
@RunWith(AndroidJUnit4::class)
class MaintenanceDaoTest {

    private lateinit var db: AppDatabase
    private var home = 0L
    private var cottage = 0L

    @Before
    fun setUp() = runTest {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        home = db.locationDao().insert(SavedLocation(name = "Home", latitude = 43.59, longitude = -79.64))
        cottage = db.locationDao().insert(SavedLocation(name = "Cottage", latitude = 45.0, longitude = -79.3))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun log(location: Long, type: TaskType, at: Long) =
        db.maintenanceDao().insert(MaintenanceLog(locationId = location, taskType = type, completedAt = at))

    @Test
    fun lastCompletedIsPerLocationAndTask() = runTest {
        log(home, TaskType.MOW, 1_000)
        log(home, TaskType.MOW, 3_000)
        log(home, TaskType.CLEAR_SNOW, 5_000)
        log(cottage, TaskType.MOW, 9_000)

        val dao = db.maintenanceDao()
        assertEquals(3_000L, dao.lastCompleted(home, TaskType.MOW))
        assertEquals(5_000L, dao.lastCompleted(home, TaskType.CLEAR_SNOW))
        assertEquals(9_000L, dao.lastCompleted(cottage, TaskType.MOW))
        assertNull(dao.lastCompleted(cottage, TaskType.CLEAR_SNOW))
    }

    @Test
    fun statsGroupByTaskWithinTheTimeWindow() = runTest {
        log(home, TaskType.MOW, 500) // before the window
        log(home, TaskType.MOW, 2_000)
        log(home, TaskType.MOW, 4_000)
        log(home, TaskType.CLEAR_SNOW, 3_000)

        val stats = db.maintenanceDao().observeStats(home, since = 1_000).first().associateBy { it.taskType }
        assertEquals(2, stats.getValue(TaskType.MOW).count)
        assertEquals(4_000L, stats.getValue(TaskType.MOW).lastCompletedAt)
        assertEquals(1, stats.getValue(TaskType.CLEAR_SNOW).count)
    }

    @Test
    fun historyIsNewestFirst() = runTest {
        log(home, TaskType.MOW, 1_000)
        log(home, TaskType.CLEAR_SNOW, 3_000)
        log(home, TaskType.MOW, 2_000)
        val times = db.maintenanceDao().observeForLocation(home).first().map { it.completedAt }
        assertEquals(listOf(3_000L, 2_000L, 1_000L), times)
    }

    @Test
    fun deletingALocationCascadesToItsLogs() = runTest {
        log(cottage, TaskType.MOW, 1_000)
        val location = db.locationDao().observeAll().first().first { it.id == cottage }
        db.locationDao().delete(location)
        assertTrue(db.maintenanceDao().observeForLocation(cottage).first().isEmpty())
    }

    @Test
    fun onlyOneLocationIsSelectedAndDuplicatesAreReused() = runTest {
        val dao = db.locationDao()
        dao.select(home)
        dao.select(cottage)
        assertEquals(cottage, dao.getSelected()?.id)
        assertEquals(1, dao.observeAll().first().count { it.isSelected })

        // Saving a place within ~1 km of an existing one re-uses that row
        val id = dao.saveAndSelect(SavedLocation(name = "Home again", latitude = 43.591, longitude = -79.641))
        assertEquals(home, id)
        assertEquals(2, dao.observeAll().first().size)
    }

    @Test
    fun eachStormIsOnlyAlertedOnce() = runTest {
        val alerts = db.snowAlertDao()
        val record = SnowAlertRecord(locationId = home, eventStart = 10_000, expectedCm = 8.0, notifiedAt = 1)
        assertTrue(alerts.insert(record) > 0)
        assertEquals(-1L, alerts.insert(record.copy(notifiedAt = 2)))
        assertEquals(1, alerts.recent(home).size)
    }

    @Test
    fun unsyncedLogsAreTrackedForCloudBackup() = runTest {
        val a = log(home, TaskType.MOW, 1_000)
        log(home, TaskType.MOW, 2_000)
        val dao = db.maintenanceDao()
        assertEquals(2, dao.unsynced().size)
        dao.markSynced(listOf(a))
        assertEquals(1, dao.unsynced().size)
    }
}
