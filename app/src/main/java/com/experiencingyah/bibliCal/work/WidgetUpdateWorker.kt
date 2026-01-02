package com.experiencingyah.bibliCal.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.experiencingyah.bibliCal.widgets.DateWidgetProvider
import com.experiencingyah.bibliCal.widgets.ShabbatWidgetProvider
import com.experiencingyah.bibliCal.widgets.CombinedWidgetProvider

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

