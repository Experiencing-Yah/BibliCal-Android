package com.experiencingyah.bibliCal.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import com.experiencingyah.bibliCal.util.SunsetCalculator
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Worker that triggers exactly at sunset to update the status notification
 * with the new biblical date. After running, it schedules itself to run
 * again at the next sunset.
 */
class SunsetTickWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "SunsetTickWorker running - updating status for new biblical day")

        val repo = LunarRepository(applicationContext)
        val settings = SettingsRepository(applicationContext)

        // Update the status notification and widgets
        StatusUpdater.updateStatusAndWidgets(applicationContext, repo, settings)

        // Schedule the next sunset tick
        scheduleNextSunsetTick(applicationContext, settings)

        return Result.success()
    }

    companion object {
        private const val TAG = "SunsetTickWorker"
        const val UNIQUE_WORK_NAME = "sunset_tick"
        
        // Fallback delay if sunset calculation fails (60 minutes)
        private const val FALLBACK_DELAY_MINUTES = 60L
        
        // Small buffer after sunset to ensure we're definitely in the new day (30 seconds)
        private const val SUNSET_BUFFER_SECONDS = 30L

        /**
         * Schedules the next sunset tick worker to run at the next sunset.
         * If sunset calculation fails, schedules with a fallback delay.
         */
        suspend fun scheduleNextSunsetTick(context: Context, settings: SettingsRepository? = null) {
            val settingsRepo = settings ?: SettingsRepository(context)
            val (latitude, longitude) = StatusUpdater.getLocation(settingsRepo)
            val zoneId = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zoneId)

            // Calculate the next sunset
            val nextSunset = SunsetCalculator.calculateNextSunset(
                latitude,
                longitude,
                zoneId.id
            )

            val delayMillis = if (nextSunset != null) {
                // Add a small buffer to ensure we're past sunset
                val targetTime = nextSunset.plusSeconds(SUNSET_BUFFER_SECONDS)
                val delay = Duration.between(now, targetTime).toMillis()
                
                // Ensure delay is positive (at least 1 second)
                if (delay > 0) {
                    Log.d(TAG, "Scheduling next sunset tick for $targetTime (delay: ${delay}ms)")
                    delay
                } else {
                    // Sunset just passed, schedule for tomorrow's sunset
                    val tomorrowSunset = SunsetCalculator.calculateSunsetTime(
                        now.toLocalDate().plusDays(1),
                        latitude,
                        longitude,
                        zoneId
                    )
                    if (tomorrowSunset != null) {
                        val tomorrowTarget = tomorrowSunset.plusSeconds(SUNSET_BUFFER_SECONDS)
                        val tomorrowDelay = Duration.between(now, tomorrowTarget).toMillis()
                        Log.d(TAG, "Scheduling for tomorrow's sunset: $tomorrowTarget (delay: ${tomorrowDelay}ms)")
                        tomorrowDelay.coerceAtLeast(1000L)
                    } else {
                        Log.w(TAG, "Could not calculate tomorrow's sunset, using fallback delay")
                        TimeUnit.MINUTES.toMillis(FALLBACK_DELAY_MINUTES)
                    }
                }
            } else {
                Log.w(TAG, "Could not calculate next sunset, using fallback delay of $FALLBACK_DELAY_MINUTES minutes")
                TimeUnit.MINUTES.toMillis(FALLBACK_DELAY_MINUTES)
            }

            val workRequest = OneTimeWorkRequestBuilder<SunsetTickWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            
            Log.d(TAG, "Sunset tick scheduled with delay: ${delayMillis}ms (${delayMillis / 1000 / 60} minutes)")
        }
    }
}
