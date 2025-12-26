package com.example.biblicalmonth.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AppSchedulers {
    private const val UNIQUE_DAILY_TICK = "daily_tick"
    private const val UNIQUE_WIDGET_UPDATE = "widget_update"

    fun ensureScheduled(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        // Schedule daily tick worker (runs every 12 hours)
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
    }
}

