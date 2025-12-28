package com.example.biblicalmonth.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.biblicalmonth.widgets.DateWidgetProvider
import com.example.biblicalmonth.widgets.ShabbatWidgetProvider
import com.example.biblicalmonth.widgets.CombinedWidgetProvider

class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // Update all widget types
        DateWidgetProvider.updateAll(applicationContext)
        ShabbatWidgetProvider.updateAll(applicationContext)
        CombinedWidgetProvider.updateAll(applicationContext)
        return Result.success()
    }
}

