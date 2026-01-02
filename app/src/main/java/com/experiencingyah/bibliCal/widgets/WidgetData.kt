package com.experiencingyah.bibliCal.widgets

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import com.experiencingyah.bibliCal.util.MonthNames
import com.experiencingyah.bibliCal.util.SunsetCalculator
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

data class WidgetData(
    val lunarText: String,
    val gregorianText: String,
    val shabbatText: String,
    val shabbatLabel: String,
    val isShabbat: Boolean
)

object WidgetHelper {
    fun getWidgetData(context: Context): WidgetData = runBlocking {
        withContext(Dispatchers.IO) {
            // Try to get location from cache first (fast and reliable for widgets)
            val settings = SettingsRepository(context)
            val cachedLocation = try {
                settings.getCachedLocation()
            } catch (e: Exception) {
                null
            }
            
            val lat: Double
            val lon: Double
            
            if (cachedLocation != null) {
                lat = cachedLocation.first
                lon = cachedLocation.second
            } else {
                // Cache miss or stale - try to get fresh location
                val location = getLocationWithPermissions(context)
                if (location != null) {
                    lat = location.latitude
                    lon = location.longitude
                    // Cache the fresh location for next time
                    try {
                        settings.cacheLocation(lat, lon)
                    } catch (e: Exception) {
                        // Ignore caching errors
                    }
                } else {
                    // Fallback to default coordinates
                    lat = 40.0
                    lon = -74.0
                }
            }
            val repo = LunarRepository(context)
            
            // Calculate sunset dates FIRST to determine which Gregorian date to use
            val todayDate = LocalDate.now()
            val zoneId = ZoneId.systemDefault()
            
            val todaySunset = SunsetCalculator.calculateSunsetTime(todayDate, lat, lon, zoneId)
            val tomorrowSunset = SunsetCalculator.calculateSunsetTime(todayDate.plusDays(1), lat, lon, zoneId)
            
            val now = ZonedDateTime.now(zoneId)
            val isAfterTodaySunset = todaySunset != null && now.isAfter(todaySunset)
            
            // Determine which dates to show and which date to use for biblical calculation
            val sunsetDate: LocalDate
            val nextSunsetDate: LocalDate
            val dateToUseForBiblical: LocalDate
            
            if (isAfterTodaySunset) {
                // After sunset: biblical day has advanced
                sunsetDate = todayDate // When the biblical day started
                nextSunsetDate = todayDate.plusDays(1)
                dateToUseForBiblical = todayDate.plusDays(1) // Use tomorrow for biblical calculation
            } else {
                // Before sunset: still in current biblical day
                sunsetDate = todayDate.minusDays(1) // When the current biblical day started
                nextSunsetDate = todayDate
                dateToUseForBiblical = todayDate // Use today for biblical calculation
            }
            
            // Get biblical date using the appropriate Gregorian date
            val today = repo.resolveFor(dateToUseForBiblical)
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
            
            val gregorianText = if (todaySunset != null && tomorrowSunset != null) {
                val format = DateTimeFormatter.ofPattern("M/d")
                "Sunset ${sunsetDate.format(format)} - Sunset ${nextSunsetDate.format(format)}"
            } else {
                todayDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            }
            
            // Calculate Shabbat info
            val isShabbat = isCurrentlyShabbat(now, todayDate, lat, lon)
            val shabbatLabel = if (isShabbat) "" else "Until Shabbat"
            val shabbatText = if (isShabbat) "Shabbat\nShalom" else calculateShabbatCountdown(lat, lon)
            
            WidgetData(lunarText, gregorianText, shabbatText, shabbatLabel, isShabbat)
        }
    }
    
    private fun isCurrentlyShabbat(now: ZonedDateTime, today: LocalDate, latitude: Double, longitude: Double): Boolean {
        // Shabbat is from Friday sunset to Saturday sunset
        val zoneId = ZoneId.systemDefault()
        
        // Check if it's Friday after sunset
        if (today.dayOfWeek == DayOfWeek.FRIDAY) {
            val fridaySunset = SunsetCalculator.calculateSunsetTime(today, latitude, longitude, zoneId)
                ?: today.atTime(18, 0).atZone(zoneId)
            if (now.isAfter(fridaySunset)) {
                return true
            }
        }
        
        // Check if it's Saturday before sunset
        if (today.dayOfWeek == DayOfWeek.SATURDAY) {
            val saturdaySunset = SunsetCalculator.calculateSunsetTime(today, latitude, longitude, zoneId)
                ?: today.atTime(18, 0).atZone(zoneId)
            val yesterday = today.minusDays(1)
            val fridaySunset = SunsetCalculator.calculateSunsetTime(yesterday, latitude, longitude, zoneId)
                ?: yesterday.atTime(18, 0).atZone(zoneId)
            
            return now.isAfter(fridaySunset) && now.isBefore(saturdaySunset)
        }
        
        return false
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
    
    /**
     * Get location with proper permission checks.
     * Widgets run in a restricted context, so we need to check permissions first.
     * Tries getCurrentLocation first, then falls back to lastKnownLocation.
     */
    private fun getLocationWithPermissions(context: Context): Location? {
        // Check if location permissions are granted
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasFineLocation && !hasCoarseLocation) {
            return null
        }
        
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        
        // First, try to get current location
        try {
            val cancellationTokenSource = CancellationTokenSource()
            val locationTask = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.token
            )
            // Increase timeout to 10 seconds for better chance of getting location
            val location = Tasks.await(locationTask, 10, TimeUnit.SECONDS)
            if (location != null) {
                return location
            }
        } catch (e: SecurityException) {
            // Permission issue
        } catch (e: Exception) {
            // Ignore and try last known location
        }
        
        // Fallback to last known location
        try {
            val lastLocationTask = fusedLocationClient.lastLocation
            val lastLocation = Tasks.await(lastLocationTask, 2, TimeUnit.SECONDS)
            if (lastLocation != null) {
                // Check if last location is recent (within 1 hour)
                val ageMillis = System.currentTimeMillis() - lastLocation.time
                val ageHours = ageMillis / (1000 * 60 * 60)
                if (ageHours < 1) {
                    return lastLocation
                }
            }
        } catch (e: SecurityException) {
            // Permission issue
        } catch (e: Exception) {
            // Ignore
        }
        
        return null
    }
}

