package com.example.biblicalmonth.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.biblicalmonth.R
import com.example.biblicalmonth.data.LunarRepository
import com.example.biblicalmonth.data.settings.SettingsRepository
import com.example.biblicalmonth.ui.MainActivity
import com.example.biblicalmonth.util.MonthNames
import com.example.biblicalmonth.util.SunsetCalculator
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context, appWidgetManager, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // When widget is first added, ensure periodic updates are scheduled
        com.example.biblicalmonth.work.AppSchedulers.ensureScheduled(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(android.content.ComponentName(context, TodayWidgetProvider::class.java))
            updateAll(context, mgr, ids)
        }

        private fun updateAll(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return

            val (lunarText, gregorianText, shabbatCountdown) = runBlocking {
                withContext(Dispatchers.IO) {
                    val repo = LunarRepository(context)
                    val today = repo.getToday()
                    val lunarText = if (today == null) {
                        "Tap to set an anchor"
                    } else {
                        val dayOrdinal = when (today.dayOfMonth) {
                            1 -> "1st"
                            2 -> "2nd"
                            3 -> "3rd"
                            21 -> "21st"
                            22 -> "22nd"
                            23 -> "23rd"
                            31 -> "31st"
                            else -> "${today.dayOfMonth}th"
                        }
                        val monthOrdinal = when (today.monthNumber) {
                            1 -> "1st"
                            2 -> "2nd"
                            3 -> "3rd"
                            else -> "${today.monthNumber}th"
                        }
                        "$dayOrdinal Day of $monthOrdinal Month (${today.yearNumber})"
                    }
                    // Calculate sunset dates for today and tomorrow
                    val todayDate = LocalDate.now()
                    val zoneId = ZoneId.systemDefault()
                    
                    // Get user's location
                    val location = try {
                        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                        val cancellationTokenSource = CancellationTokenSource()
                        val locationTask = fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                            cancellationTokenSource.token
                        )
                        Tasks.await(locationTask, 5, TimeUnit.SECONDS)
                    } catch (e: Exception) {
                        null
                    }
                    
                    // Use actual location or fallback to default coordinates (40.0, -74.0)
                    val latitude = location?.latitude ?: 40.0
                    val longitude = location?.longitude ?: -74.0
                    
                    val todaySunset = SunsetCalculator.calculateSunsetTime(todayDate, latitude, longitude, zoneId)
                    val tomorrowSunset = SunsetCalculator.calculateSunsetTime(todayDate.plusDays(1), latitude, longitude, zoneId)
                    
                    val now = ZonedDateTime.now(zoneId)
                    val isAfterTodaySunset = todaySunset != null && now.isAfter(todaySunset)
                    
                    // Determine which dates to show - sunset date is yesterday if before today's sunset, today if after
                    val sunsetDate = if (isAfterTodaySunset) todayDate else todayDate.minusDays(1)
                    val nextSunsetDate = if (isAfterTodaySunset) todayDate.plusDays(1) else todayDate
                    
                    val gregorianText = if (todaySunset != null && tomorrowSunset != null) {
                        val format = DateTimeFormatter.ofPattern("M/d")
                        "Sunset ${sunsetDate.format(format)} - Sunset ${nextSunsetDate.format(format)}"
                    } else {
                        todayDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    }
                    
                    // Shabbat countdown will be calculated later with location
                    Triple(lunarText, gregorianText, "")
                }
            }

            val clickPi = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Get location once for all widget updates
            val location = try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                val cancellationTokenSource = CancellationTokenSource()
                val locationTask = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationTokenSource.token
                )
                Tasks.await(locationTask, 5, TimeUnit.SECONDS)
            } catch (e: Exception) {
                null
            }
            val latitude = location?.latitude ?: 40.0
            val longitude = location?.longitude ?: -74.0
            
            ids.forEach { id ->
                // Check if it's currently Shabbat
                val now = ZonedDateTime.now(ZoneId.systemDefault())
                val today = LocalDate.now()
                val isShabbat = isCurrentlyShabbat(now, today, latitude, longitude)
                val shabbatLabel = if (isShabbat) "" else "Until Shabbat"
                val displayText = if (isShabbat) "Enjoy Shabbat" else calculateShabbatCountdown(latitude, longitude)
                
                val views = RemoteViews(context.packageName, R.layout.today_widget).apply {
                    setTextViewText(R.id.widget_lunar_date, lunarText)
                    setTextViewText(R.id.widget_gregorian_date, gregorianText)
                    setTextViewText(R.id.widget_shabbat_countdown, displayText)
                    setTextViewText(R.id.widget_shabbat_label, shabbatLabel)
                    // Set gold color for Shabbat text
                    if (isShabbat) {
                        setTextColor(R.id.widget_shabbat_countdown, 0xFFFFD700.toInt())
                    } else {
                        setTextColor(R.id.widget_shabbat_countdown, 0xFFFFFFFF.toInt())
                    }
                    setOnClickPendingIntent(R.id.widget_lunar_date, clickPi)
                    setOnClickPendingIntent(R.id.widget_gregorian_date, clickPi)
                    setOnClickPendingIntent(R.id.widget_shabbat_countdown, clickPi)
                    setOnClickPendingIntent(R.id.widget_shabbat_label, clickPi)
                }
                mgr.updateAppWidget(id, views)
            }
        }
        
        private fun isCurrentlyShabbat(now: ZonedDateTime, today: LocalDate, latitude: Double, longitude: Double): Boolean {
            // Shabbat is from Friday sunset to Saturday sunset
            val isSaturday = today.dayOfWeek == DayOfWeek.SATURDAY
            if (!isSaturday) return false
            
            // If it's Saturday, check if we're before sunset
            val zoneId = ZoneId.systemDefault()
            val saturdaySunset = SunsetCalculator.calculateSunsetTime(today, latitude, longitude, zoneId)
                ?: today.atTime(18, 0).atZone(zoneId)
            
            // Also check if it's after Friday sunset
            val yesterday = today.minusDays(1)
            val fridaySunset = SunsetCalculator.calculateSunsetTime(yesterday, latitude, longitude, zoneId)
                ?: yesterday.atTime(18, 0).atZone(zoneId)
            
            return now.isAfter(fridaySunset) && now.isBefore(saturdaySunset)
        }
        
        private fun calculateShabbatCountdown(latitude: Double, longitude: Double): String {
            // Use the same timezone handling as Today screen
            val zoneId = ZoneId.systemDefault()
            val timeZoneString = zoneId.id
            val zoneIdFromString = ZoneId.of(timeZoneString)
            val now = ZonedDateTime.now(zoneIdFromString)
            val today = now.toLocalDate()
            
            // Find next Friday
            val daysUntilFriday = (DayOfWeek.FRIDAY.value - today.dayOfWeek.value + 7) % 7
            val nextFriday = if (daysUntilFriday == 0 && today.dayOfWeek == DayOfWeek.FRIDAY) {
                // If it's Friday, check if we're before sunset - if so, use today, else next Friday
                val fridaySunset = SunsetCalculator.calculateSunsetTime(today, latitude, longitude, zoneIdFromString)
                if (fridaySunset != null && now.isBefore(fridaySunset)) {
                    today
                } else {
                    today.plusDays(7)
                }
            } else {
                today.plusDays(daysUntilFriday.toLong())
            }
            
            // Calculate sunset time for Friday using the same method as Today screen
            // This matches exactly how the Today screen calculates sunset times
            val fridaySunset = SunsetCalculator.calculateSunsetTime(nextFriday, latitude, longitude, zoneIdFromString)
                ?: nextFriday.atTime(18, 0).atZone(zoneIdFromString) // Fallback to 6 PM
            
            // Both now and fridaySunset are in the same timezone (zoneIdFromString)
            // Calculate duration directly
            val duration = Duration.between(now, fridaySunset)
            val totalSeconds = duration.seconds
            
            return when {
                totalSeconds < 0 -> "Shabbat passed"
                totalSeconds < 3600 -> {
                    // Less than 1 hour: show minutes only
                    val minutes = (totalSeconds / 60.0).roundToInt()
                    "${minutes}m"
                }
                totalSeconds < 86400 -> {
                    // Less than 1 day: show hours and minutes
                    val totalMinutes = (totalSeconds / 60.0).roundToInt()
                    val hours = totalMinutes / 60
                    val minutes = totalMinutes % 60
                    "${hours}h ${minutes}m"
                }
                else -> {
                    // 1 day or more: show days and hours
                    // Calculate days and remaining hours separately to avoid rounding errors
                    val totalMinutes = totalSeconds / 60
                    val days = totalMinutes / (24 * 60)
                    val remainingMinutes = totalMinutes % (24 * 60)
                    val hours = remainingMinutes / 60
                    "${days}d ${hours}h"
                }
            }
        }
    }
}

