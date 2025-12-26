package com.example.biblicalmonth.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.*
import kotlin.math.roundToInt

/**
 * Calculates sunset time for a given date and location.
 * Uses approximate astronomical calculations.
 */
object SunsetCalculator {
    /**
     * Calculate sunset time for a given date and location.
     * @param date The date to calculate sunset for
     * @param latitude Latitude in degrees (positive for north, negative for south)
     * @param longitude Longitude in degrees (positive for east, negative for west)
     * @param timeZone The timezone identifier (e.g., "America/New_York")
     * @return LocalDateTime of sunset, or null if calculation fails
     */
    fun calculateSunset(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        timeZone: String = "UTC"
    ): LocalDateTime? {
        return try {
            val zoneId = ZoneId.of(timeZone)
            val zonedDate = date.atStartOfDay(zoneId)
            val sunset = calculateSunsetTime(date, latitude, longitude, zoneId)
            sunset?.toLocalDateTime()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calculate the next sunset from now.
     * If today's sunset has passed, returns tomorrow's sunset.
     */
    fun calculateNextSunset(
        latitude: Double,
        longitude: Double,
        timeZone: String = "UTC"
    ): ZonedDateTime? {
        val zoneId = ZoneId.of(timeZone)
        val now = ZonedDateTime.now(zoneId)
        val today = now.toLocalDate()
        
        val todaySunset = calculateSunsetTime(today, latitude, longitude, zoneId)
        return if (todaySunset != null && todaySunset.isAfter(now)) {
            todaySunset
        } else {
            calculateSunsetTime(today.plusDays(1), latitude, longitude, zoneId)
        }
    }

    fun calculateSunsetTime(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId
    ): ZonedDateTime? {
        try {
            // Convert to radians
            val latRad = Math.toRadians(latitude)
            
            // Calculate day of year
            val dayOfYear = date.dayOfYear
            
            // Solar declination (approximate)
            val declination = 23.45 * sin(Math.toRadians(360.0 * (284 + dayOfYear) / 365.0))
            val declRad = Math.toRadians(declination)
            
            // Hour angle
            val hourAngle = acos(-tan(latRad) * tan(declRad))
            
            // Equation of time (approximate correction in minutes)
            val B = (360.0 / 365.0) * (dayOfYear - 81)
            val equationOfTime = 9.87 * sin(2 * Math.toRadians(B)) - 7.53 * cos(Math.toRadians(B)) - 1.5 * sin(Math.toRadians(B))
            
            // Calculate sunset in local solar time (hours from midnight)
            val sunsetSolarTime = 12.0 + (hourAngle * 12.0 / PI) - (equationOfTime / 60.0)
            
            // Convert solar time to standard time
            // Longitude offset: each degree of longitude = 4 minutes
            val longitudeOffsetHours = longitude / 15.0
            
            // Use an approximate sunset time (6 PM) to get the correct DST offset
            // This is better than using noon since sunset is typically closer to 6 PM
            val approximateSunsetTime = date.atTime(18, 0).atZone(zoneId)
            val utcOffsetSeconds = approximateSunsetTime.offset.totalSeconds
            val utcOffsetHours = utcOffsetSeconds / 3600.0
            
            // Standard meridian for timezone (approximate - most timezones are multiples of 15 degrees)
            val standardMeridian = (utcOffsetHours * 15.0).roundToInt().toDouble()
            val meridianOffsetHours = (longitude - standardMeridian) / 15.0
            
            // Final sunset time in local solar time
            val sunsetHour = sunsetSolarTime - meridianOffsetHours
            
            // Normalize to 0-24 range
            val normalizedHour = ((sunsetHour % 24) + 24) % 24
            
            // Convert to local time with better precision
            val hours = normalizedHour.toInt()
            val fractionalMinutes = (normalizedHour - hours) * 60.0
            val minutes = fractionalMinutes.toInt()
            val fractionalSeconds = (fractionalMinutes - minutes) * 60.0
            val seconds = fractionalSeconds.toInt()
            
            // Create the sunset time in local timezone
            // atZone will automatically apply the correct DST rules for this specific date and time
            val sunset = date.atTime(hours, minutes, seconds.coerceIn(0, 59))
            val sunsetZoned = sunset.atZone(zoneId)
            
            // Verify the offset matches - if not, the DST status might have changed
            // Recalculate with the actual sunset time's offset
            val actualOffset = sunsetZoned.offset.totalSeconds
            if (actualOffset != utcOffsetSeconds) {
                val correctedUtcOffsetHours = actualOffset / 3600.0
                val correctedStandardMeridian = (correctedUtcOffsetHours * 15.0).roundToInt().toDouble()
                val correctedMeridianOffsetHours = (longitude - correctedStandardMeridian) / 15.0
                val correctedSunsetHour = sunsetSolarTime - correctedMeridianOffsetHours
                val correctedNormalizedHour = ((correctedSunsetHour % 24) + 24) % 24
                val correctedHours = correctedNormalizedHour.toInt()
                val correctedFractionalMinutes = (correctedNormalizedHour - correctedHours) * 60.0
                val correctedMinutes = correctedFractionalMinutes.toInt()
                val correctedSeconds = ((correctedFractionalMinutes - correctedMinutes) * 60.0).toInt()
                val correctedSunset = date.atTime(correctedHours, correctedMinutes, correctedSeconds.coerceIn(0, 59))
                return correctedSunset.atZone(zoneId)
            }
            
            return sunsetZoned
        } catch (e: Exception) {
            return null
        }
    }
}

