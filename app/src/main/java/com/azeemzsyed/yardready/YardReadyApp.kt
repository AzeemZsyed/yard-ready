package com.azeemzsyed.yardready

import android.app.Application
import com.azeemzsyed.yardready.notifications.SnowAlertNotifier
import com.azeemzsyed.yardready.work.ForecastCheckWorker

class YardReadyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        SnowAlertNotifier.createChannel(this)
        ForecastCheckWorker.schedule(this)
    }
}
