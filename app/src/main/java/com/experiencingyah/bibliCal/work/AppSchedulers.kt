package com.experiencingyah.bibliCal.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object AppSchedulers {
    private const val UNIQUE_DAILY_TICK = "daily_tick"
    private const val UNIQUE_WIDGET_UPDATE = "widget_update"

    fun ensureScheduled(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        // Schedule daily tick worker (runs every 12 hours for prompts and reminders)
        val dailyRequest = PeriodicWorkRequestBuilder<DailyTickWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_DAILY_TICK, ExistingPeriodicWorkPolicy.UPDATE, dailyRequest)

        // Schedule widget update worker (runs every 15 minutes)
        // Note: PeriodicWorkRequest minimum interval is 15 minutes
        val widgetRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_WIDGET_UPDATE, ExistingPeriodicWorkPolicy.UPDATE, widgetRequest)

        // Schedule sunset tick worker (runs at sunset to update notification with new biblical day)
        // This uses a coroutine because it needs to read settings for location
        CoroutineScope(Dispatchers.IO).launch {
            SunsetTickWorker.scheduleNextSunsetTick(context)
        }
    }
}

