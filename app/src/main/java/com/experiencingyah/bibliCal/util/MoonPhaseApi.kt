package com.experiencingyah.bibliCal.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.*

/**
 * Calculates new moon dates using astronomical algorithms.
 * No external API required - works completely offline.
 * Based on Meeus' algorithms for calculating lunar phases.
 */
object MoonPhaseApi {
    private const val TAG = "MoonPhaseApi"
    
    // Reference epoch: J2000.0 (January 1, 2000, 12:00 UTC)
    private const val J2000_EPOCH = 2451545.0
    
    /**
     * Get the most recent new moon conjunction date before or on the given date.
     * Uses astronomical calculations - no API required.
     * 
     * @param beforeOrOnDate The date to look back from
     * @return The most recent new moon conjunction date
     */
    suspend fun getMostRecentNewMoon(beforeOrOnDate: LocalDate): LocalDate? {
        return withContext(Dispatchers.IO) {
            try {
                calculateNewMoonAstronomically(beforeOrOnDate)
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating new moon", e)
                null
            }
        }
    }
    
    /**
     * Calculate new moon date using astronomical algorithms.
     * Based on Meeus' simplified lunar phase calculation.
     */
    private fun calculateNewMoonAstronomically(targetDate: LocalDate): LocalDate? {
        try {
            // Convert target date to Julian Day
            val targetJD = dateToJulianDay(targetDate)
            
            // Calculate approximate new moon (k value)
            // k represents the lunation number since J2000.0
            // The first new moon of 2000 was approximately at JD 2451550.1 (k=0)
            val meanSynodicMonth = 29.530588861
            val firstNewMoon2000 = 2451550.09766
            val daysSinceFirstNewMoon = targetJD - firstNewMoon2000
            val estimatedK = (daysSinceFirstNewMoon / meanSynodicMonth).toInt()
            
            // Try a range of k values to find the most recent new moon
            var bestNewMoon: LocalDate? = null
            var bestJD = 0.0
            
            // Search from estimatedK down to estimatedK-10 to ensure we find it
            for (kOffset in 0..10) {
                try {
                    val testK = estimatedK - kOffset
                    val newMoonJD = calculateNewMoonJD(testK)
                    val newMoonDate = julianDayToDate(newMoonJD)
                    
                    if (newMoonJD <= targetJD && (bestNewMoon == null || newMoonJD > bestJD)) {
                        bestJD = newMoonJD
                        bestNewMoon = newMoonDate
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error testing k=${estimatedK - kOffset}", e)
                    // Continue to next iteration
                }
            }
            
            if (bestNewMoon == null) {
                Log.e(TAG, "No new moon found in search range! Using fallback.")
                // As a last resort, try one more month back
                val fallbackK = estimatedK - 11
                val fallbackJD = calculateNewMoonJD(fallbackK)
                bestNewMoon = julianDayToDate(fallbackJD)
            }
            
            return bestNewMoon
        } catch (e: Exception) {
            Log.e(TAG, "Error in astronomical calculation", e)
            return null
        }
    }
    
    /**
     * Calculate Julian Day number for a new moon.
     * Based on Meeus' algorithm (Astronomical Algorithms, Chapter 49).
     * 
     * @param k The lunation number (0 = first new moon of 2000)
     * @return Julian Day of the new moon
     */
    private fun calculateNewMoonJD(k: Int): Double {
        // Time in Julian centuries since J2000.0
        val T = k / 1236.85
        
        // Mean time of new moon
        val JDE = 2451550.09766 + 29.530588861 * k + 
                  0.00015437 * T * T - 
                  0.000000150 * T * T * T + 
                  0.00000000073 * T * T * T * T
        
        // Mean elongation of the Moon
        val D_raw = 297.8501921 + 445267.1114034 * T - 
                0.0018819 * T * T + 
                0.0000018319 * T * T * T - 
                0.00000000088 * T * T * T * T
        val D = ((D_raw % 360.0) + 360.0) % 360.0
        
        // Mean anomaly of the Sun
        val M_raw = 357.5291092 + 35999.0502909 * T - 
                0.0001536 * T * T + 
                0.000000048 * T * T * T
        val M = ((M_raw % 360.0) + 360.0) % 360.0
        
        // Mean anomaly of the Moon
        val F_raw = 134.9633964 + 483202.0175233 * T - 
                0.0036539 * T * T - 
                0.00000000217 * T * T * T
        val F = ((F_raw % 360.0) + 360.0) % 360.0
        
        // Convert to radians
        val D_rad = Math.toRadians(D)
        val M_rad = Math.toRadians(M)
        val F_rad = Math.toRadians(F)
        
        // Periodic corrections (simplified - full Meeus algorithm has many more terms)
        var correction = -0.40720 * sin(F_rad)
        correction += 0.17241 * sin(M_rad)
        correction += 0.01608 * sin(2 * F_rad)
        correction += 0.01039 * sin(2 * D_rad - M_rad)
        correction += 0.00739 * sin(2 * D_rad)
        correction -= 0.00514 * sin(2 * M_rad)
        correction += 0.00208 * sin(2 * F_rad - 2 * D_rad)
        correction -= 0.00111 * sin(2 * F_rad - M_rad)
        correction -= 0.00057 * sin(2 * D_rad + M_rad)
        correction += 0.00056 * sin(4 * D_rad - M_rad)
        correction -= 0.00042 * sin(3 * M_rad)
        correction += 0.00042 * sin(2 * F_rad + 2 * D_rad)
        correction += 0.00038 * sin(4 * D_rad)
        correction -= 0.00024 * sin(2 * F_rad - 3 * M_rad)
        correction -= 0.00017 * sin(D_rad - M_rad)
        correction -= 0.00007 * sin(2 * D_rad + 2 * F_rad)
        correction += 0.00004 * sin(2 * F_rad + M_rad)
        correction += 0.00004 * sin(4 * F_rad)
        correction -= 0.00003 * sin(2 * D_rad - 3 * M_rad)
        correction += 0.00003 * sin(2 * D_rad + M_rad - 2 * F_rad)
        correction -= 0.00002 * sin(2 * D_rad - 2 * F_rad - M_rad)
        correction -= 0.00002 * sin(3 * D_rad - 2 * F_rad)
        correction += 0.00002 * sin(4 * D_rad - M_rad - 2 * F_rad)
        
        return JDE + correction
    }
    
    /**
     * Convert LocalDate to Julian Day number.
     */
    private fun dateToJulianDay(date: LocalDate): Double {
        val year = date.year
        val month = date.monthValue
        val day = date.dayOfMonth
        
        var a = (14 - month) / 12
        var y = year + 4800 - a
        var m = month + 12 * a - 3
        
        var jdn = day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        
        // Add fraction for noon UTC
        return jdn.toDouble() + 0.5
    }
    
    /**
     * Convert Julian Day number to LocalDate.
     * Uses a more reliable algorithm from Fliegel & Van Flandern (1968).
     */
    private fun julianDayToDate(jd: Double): LocalDate {
        try {
            val j = (jd + 0.5).toLong()
            
            var l = j + 68569L
            val n = (4 * l / 146097).toInt()
            l = l - (146097L * n + 3) / 4
            val i = (4000 * (l + 1) / 1461001).toInt()
            l = l - (1461L * i / 4) + 31
            val j_month = (80 * l / 2447).toInt()
            val day = (l - 2447L * j_month / 80).toInt()
            val l_temp = j_month / 11
            val month = (j_month + 2 - 12 * l_temp).toInt()
            val year = (100 * (n - 49) + i + l_temp).toInt()
            
            // Validate the date before creating it
            if (year < 1 || year > 9999 || month < 1 || month > 12 || day < 1 || day > 31) {
                Log.e(TAG, "Invalid date calculated: year=$year, month=$month, day=$day from JD=$jd")
                throw IllegalArgumentException("Invalid date: year=$year, month=$month, day=$day")
            }
            
            return LocalDate.of(year, month, day)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting JD $jd to LocalDate", e)
            throw e
        }
    }
    
}

