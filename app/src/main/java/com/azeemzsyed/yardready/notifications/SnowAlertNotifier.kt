package com.azeemzsyed.yardready.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.azeemzsyed.yardready.R
import com.azeemzsyed.yardready.ui.Format
import com.azeemzsyed.yardready.ui.main.MainActivity
import com.azeemzsyed.yardready.domain.SnowEvent
import java.time.ZoneOffset

object SnowAlertNotifier {

    private const val CHANNEL_ID = "snow_alerts"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_snow_alerts),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_snow_alerts_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by canNotify() above
    fun notifySnowfall(context: Context, locationName: String, event: SnowEvent, zone: ZoneOffset, expectedCm: Double) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val title = context.getString(R.string.notification_snow_title, Format.cm(expectedCm), locationName)
        val text = context.getString(R.string.notification_snow_text, Format.dayTime(event.start, zone))

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_snow)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(event.start.epochSecond.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the call.
        }
    }
}
