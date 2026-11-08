package com.azeemzsyed.yardready.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.azeemzsyed.yardready.ServiceLocator
import com.azeemzsyed.yardready.data.local.SnowAlertRecord
import com.azeemzsyed.yardready.notifications.SnowAlertNotifier
import java.util.concurrent.TimeUnit

/**
 * Runs every few hours in the background: fetches the forecast, sends a
 * notification for incoming snowfall (once per storm), and retries cloud backup.
 */
class ForecastCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = ServiceLocator.database
        ServiceLocator.maintenanceRepository.syncPending()

        val settings = ServiceLocator.settings
        if (!settings.alertsEnabled.value) return Result.success()
        val location = db.locationDao().getSelected() ?: return Result.success()

        return try {
            val forecast = ServiceLocator.weatherRepository.forecast(location.latitude, location.longitude, location.name)
            val now = ServiceLocator.clock.instant()
            val outlook = ServiceLocator.engine.snowOutlook(forecast, now, settings.snowThresholdCm.value.toDouble())
            val event = outlook.nextEvent
            if (outlook.alert && event != null) {
                val inserted = db.snowAlertDao().insert(
                    SnowAlertRecord(
                        locationId = location.id,
                        eventStart = event.start.toEpochMilli(),
                        expectedCm = outlook.next24hCm,
                        notifiedAt = now.toEpochMilli(),
                    )
                )
                if (inserted != -1L) { // first time we've seen this storm
                    SnowAlertNotifier.notifySnowfall(applicationContext, location.name, event, forecast.zone, outlook.next24hCm)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Forecast check failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ForecastCheckWorker"
        private const val WORK_NAME = "forecast_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ForecastCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
