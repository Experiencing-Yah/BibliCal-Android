package com.experiencingyah.bibliCal.work

import android.content.Context
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import com.experiencingyah.bibliCal.notifications.Notifier
import com.experiencingyah.bibliCal.util.MonthNames
import com.experiencingyah.bibliCal.util.SunsetCalculator
import com.experiencingyah.bibliCal.widgets.CombinedWidgetProvider
import com.experiencingyah.bibliCal.widgets.DateWidgetProvider
import com.experiencingyah.bibliCal.widgets.ShabbatWidgetProvider
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Shared logic for updating status notification and widgets based on biblical date.
 * Used by both DailyTickWorker and SunsetTickWorker to ensure consistent behavior.
 */
object StatusUpdater {

    /**
     * Updates the status notification and all widgets with the current biblical date.
     * 
     * @param context Application context
     * @param repo LunarRepository for resolving biblical dates
     * @param settings SettingsRepository for user preferences
     * @return The resolved biblical date, or null if resolution failed
     */
    suspend fun updateStatusAndWidgets(
        context: Context,
        repo: LunarRepository,
        settings: SettingsRepository
    ): com.experiencingyah.bibliCal.domain.LunarDate? {
        Notifier.ensureChannels(context)

        // Get location (cached or default) to calculate sunset
        val cachedLocation = settings.getCachedLocation()
        val latitude = cachedLocation?.first ?: DEFAULT_LATITUDE
        val longitude = cachedLocation?.second ?: DEFAULT_LONGITUDE

        // Determine which Gregorian date to use for biblical calculation based on sunset
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val dateToUseForBiblical = getDateForBiblicalCalculation(now, latitude, longitude)

        val today = repo.resolveFor(dateToUseForBiblical)
        if (today != null) {
            val namingMode = settings.monthNamingMode.first()
            val label = "${MonthNames.format(today.monthNumber, namingMode)} ${today.dayOfMonth}, Year ${today.yearNumber}"

            if (settings.statusNotificationEnabled.first()) {
                Notifier.showOrUpdateStatus(context, today, label)
            } else {
                Notifier.cancelStatus(context)
            }

            // Update all widgets
            DateWidgetProvider.updateAll(context)
            ShabbatWidgetProvider.updateAll(context)
            CombinedWidgetProvider.updateAll(context)
        }

        return today
    }

    /**
     * Determines which Gregorian date to use for biblical calculation based on sunset.
     * If after sunset, returns tomorrow's date (the new biblical day).
     * If before sunset, returns today's date.
     */
    fun getDateForBiblicalCalculation(
        now: ZonedDateTime,
        latitude: Double,
        longitude: Double
    ): LocalDate {
        val todayDate = now.toLocalDate()
        val tomorrowDate = todayDate.plusDays(1)
        val zoneId = now.zone

        val todaySunset = SunsetCalculator.calculateSunsetTime(todayDate, latitude, longitude, zoneId)

        return if (todaySunset != null && now.isAfter(todaySunset)) {
            tomorrowDate
        } else {
            todayDate
        }
    }

    /**
     * Gets the cached location or defaults.
     */
    suspend fun getLocation(settings: SettingsRepository): Pair<Double, Double> {
        val cachedLocation = settings.getCachedLocation()
        return Pair(
            cachedLocation?.first ?: DEFAULT_LATITUDE,
            cachedLocation?.second ?: DEFAULT_LONGITUDE
        )
    }

    // Default location (approximately New York area)
    const val DEFAULT_LATITUDE = 40.0
    const val DEFAULT_LONGITUDE = -74.0
}
