package com.example.biblicalmonth.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.biblicalmonth.widgets.TodayWidgetProvider

class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // Simply update the widget
        TodayWidgetProvider.updateAll(applicationContext)
        return Result.success()
    }
}

