package com.experiencingyah.bibliCal.util

import android.util.Log
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.*

/**
 * Calculates new moon conjunction dates and estimates first sliver visibility.
 * Uses approximate astronomical calculations.
 */
object NewMoonCalculator {
    /**
     * Calculate the date of new moon conjunction (when moon is between Earth and Sun).
     * Uses a more accurate calculation with multiple reference points.
     * 
     * @param aroundDate The date around which to find the new moon
     * @return The estimated date of new moon conjunction
     */
    fun calculateNewMoonConjunction(aroundDate: LocalDate): LocalDate {
        // Use multiple reference points for better accuracy
        // Reference new moons (known accurate conjunction dates):
        val references = listOf(
            LocalDate.of(2024, 1, 11),   // Jan 11, 2024
            LocalDate.of(2024, 11, 1),    // Nov 1, 2024
            LocalDate.of(2024, 12, 1),    // Dec 1, 2024
            LocalDate.of(2025, 1, 29),    // Jan 29, 2025
            LocalDate.of(2025, 3, 1),     // Mar 1, 2025
            LocalDate.of(2025, 11, 20),   // Nov 20, 2025
            LocalDate.of(2025, 12, 19),   // Dec 19, 2025 (known from user - conjunction date)
        )
        
        // Mean synodic month length in days (29.530588 days)
        val synodicMonth = 29.530588
        val synodicMonthInt = synodicMonth.roundToInt()
        
        // Find the closest reference date before or on aroundDate
        val closestReference = references
            .filter { !it.isAfter(aroundDate) }
            .maxOrNull() ?: references.first()
        
        val referenceEpoch = closestReference.toEpochDay()
        val targetEpoch = aroundDate.toEpochDay()
        val daysSinceReference = targetEpoch - referenceEpoch
        
        // Calculate how many new moons have passed
        val newMoonsSinceReference = (daysSinceReference / synodicMonth)
        
        // Calculate the date of the most recent new moon
        val daysToMostRecentNewMoon = (newMoonsSinceReference * synodicMonth)
        val mostRecentNewMoonEpoch = referenceEpoch + daysToMostRecentNewMoon.roundToInt()
        var newMoonDate = LocalDate.ofEpochDay(mostRecentNewMoonEpoch)
        
        // Fine-tune: check if we need to go back one synodic month
        if (newMoonDate.isAfter(aroundDate)) {
            newMoonDate = newMoonDate.minusDays(synodicMonthInt.toLong())
        }
        
        // Additional refinement: if the calculated date is more than 15 days before target,
        // we might have missed one, so check the next one
        val daysDiff = targetEpoch - newMoonDate.toEpochDay()
        if (daysDiff > 15) {
            val nextNewMoon = newMoonDate.plusDays(synodicMonthInt.toLong())
            if (!nextNewMoon.isAfter(aroundDate)) {
                newMoonDate = nextNewMoon
            }
        }
        
        return newMoonDate
    }
    
    /**
     * Estimate when the first sliver of the new moon would be visible after conjunction.
     * The first sliver is typically visible 1-2 days after conjunction, depending on:
     * - Location (latitude affects visibility window)
     * - Time of year (seasonal variations)
     * - Weather conditions (not accounted for in this calculation)
     * 
     * @param latitude Latitude in degrees
     * @param longitude Longitude in degrees
     * @param aroundDate The date around which to estimate (used to calculate conjunction)
     * @return The estimated date when first sliver would be visible, or null if calculation fails
     */
    fun estimateFirstSliverVisibility(
        latitude: Double,
        longitude: Double,
        aroundDate: LocalDate
    ): LocalDate? {
        // Calculate new moon conjunction date
        val conjunctionDate = calculateNewMoonConjunction(aroundDate)
        return estimateFirstSliverVisibilityFromConjunction(latitude, longitude, conjunctionDate)
    }
    
    /**
     * Estimate when the first sliver of the new moon would be visible from a known conjunction date.
     * 
     * @param latitude Latitude in degrees
     * @param longitude Longitude in degrees
     * @param conjunctionDate The date of the new moon conjunction
     * @return The estimated date when first sliver would be visible, or null if calculation fails
     */
    private fun estimateFirstSliverVisibilityFromConjunction(
        latitude: Double,
        longitude: Double,
        conjunctionDate: LocalDate
    ): LocalDate? {
        return try {
            // First sliver visibility typically occurs 1-2 days after conjunction
            // Factors affecting visibility:
            // 1. Latitude: Near equator, visibility is typically 1 day after. Further from equator, can be 1-2 days.
            // 2. Time of year: During certain seasons, visibility can be delayed.
            // 3. Moon's age: Need at least ~17-18 hours after conjunction for visibility
            
            // Base estimate: 1 day after conjunction
            var daysAfterConjunction = 1.0
            
            // Adjust based on latitude (further from equator = slightly longer wait)
            val absLatitude = abs(latitude)
            if (absLatitude > 40) {
                // Higher latitudes may need slightly more time
                daysAfterConjunction = 1.2
            } else if (absLatitude > 30) {
                daysAfterConjunction = 1.1
            }
            
            // Check if conjunction happened late in the day - if so, first visibility might be next day
            val zoneId = ZoneId.systemDefault()
            val conjunctionDateTime = conjunctionDate.atStartOfDay(zoneId)
            
            // Estimate: if conjunction was after noon local time, add extra day
            // (This is a simplification - actual conjunction time varies)
            val estimatedConjunctionHour = 12.0 // Assume noon as average
            
            // If conjunction was late in day, visibility likely next day
            if (estimatedConjunctionHour > 15) {
                daysAfterConjunction += 0.5
            }
            
            // Round to nearest day
            val visibilityDate = conjunctionDate.plusDays(daysAfterConjunction.roundToInt().toLong())
            
            // Ensure we're looking forward (first sliver should be after or on conjunction date)
            if (visibilityDate.isBefore(conjunctionDate)) {
                conjunctionDate.plusDays(1)
            } else {
                visibilityDate
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Find the most recent new moon visibility date before or on the given date.
     * This is useful for estimating the current month start.
     * 
     * @param latitude Latitude in degrees
     * @param longitude Longitude in degrees
     * @param beforeOrOnDate The date to look back from
     * @return The estimated most recent first sliver visibility date
     */
    suspend fun findMostRecentNewMoonVisibility(
        latitude: Double,
        longitude: Double,
        beforeOrOnDate: LocalDate
    ): LocalDate? {
        return try {
            // Use astronomical calculation to get the most recent new moon conjunction
            val conjunctionDate = MoonPhaseApi.getMostRecentNewMoon(beforeOrOnDate)
            
            if (conjunctionDate == null) {
                // Fallback to simple calculation if astronomical calculation fails
                var currentConjunction = calculateNewMoonConjunction(beforeOrOnDate)
                val synodicMonth = 29.530588
                val synodicMonthInt = synodicMonth.roundToInt()
                
                if (currentConjunction.isAfter(beforeOrOnDate)) {
                    currentConjunction = currentConjunction.minusDays(synodicMonthInt.toLong())
                }
                
                val firstSliverDate = currentConjunction.plusDays(1)
                if (firstSliverDate.isAfter(beforeOrOnDate)) {
                    val previousConjunction = currentConjunction.minusDays(synodicMonthInt.toLong())
                    return previousConjunction.plusDays(1)
                }
                return firstSliverDate
            }
            
            // First sliver visibility is simply 1 day after conjunction
            val firstSliverDate = conjunctionDate.plusDays(1)
            
            // Make sure the first sliver date is not after the target date
            if (firstSliverDate.isAfter(beforeOrOnDate)) {
                // Go back one more synodic month
                val synodicMonth = 29.530588
                val synodicMonthInt = synodicMonth.roundToInt()
                val previousConjunction = conjunctionDate.minusDays(synodicMonthInt.toLong())
                val previousFirstSliver = previousConjunction.plusDays(1)
                return previousFirstSliver
            }
            
            return firstSliverDate
        } catch (e: Exception) {
            Log.e("NewMoonCalculator", "Error in findMostRecentNewMoonVisibility", e)
            null
        }
    }
    
    /**
     * Calculate the approximate date of the spring equinox for a given year.
     * The spring equinox typically occurs around March 20-21.
     * 
     * @param year The year
     * @return The approximate date of the spring equinox
     */
    fun calculateSpringEquinox(year: Int): LocalDate {
        // Spring equinox is typically March 20 or 21
        // For simplicity, we'll use March 20 as an approximation
        // More precise calculation would account for leap years and actual astronomical events
        return LocalDate.of(year, 3, 20)
    }
    
    /**
     * Estimate the biblical month number based on the spring equinox and new moon dates.
     * The biblical year starts in the spring (around the equinox), and months are counted
     * from the first new moon after or near the equinox.
     * 
     * @param latitude Latitude in degrees
     * @param longitude Longitude in degrees
     * @param currentDate The current date
     * @param newMoonDate The date of the most recent new moon visibility
     * @return The estimated month number (1-13), or null if calculation fails
     */
    fun estimateBiblicalMonth(
        latitude: Double,
        longitude: Double,
        currentDate: LocalDate,
        newMoonDate: LocalDate
    ): Int? {
        return try {
            val year = currentDate.year
            
            // Find the spring equinox for this year
            val springEquinox = calculateSpringEquinox(year)
            
            // If we're before the equinox, use last year's equinox
            val equinoxToUse = if (currentDate.isBefore(springEquinox)) {
                calculateSpringEquinox(year - 1)
            } else {
                springEquinox
            }
            
            // Find the first new moon after or near the equinox
            // Look for new moons starting from 10 days before equinox to 30 days after
            var checkDate = equinoxToUse.minusDays(10)
            var firstMonthNewMoon: LocalDate? = null
            val endDate = equinoxToUse.plusDays(30)
            
            while (checkDate.isBefore(endDate) || checkDate.isEqual(endDate)) {
                val conjunction = calculateNewMoonConjunction(checkDate)
                if (!conjunction.isBefore(equinoxToUse.minusDays(5))) {
                    val visibility = estimateFirstSliverVisibilityFromConjunction(latitude, longitude, conjunction)
                    if (visibility != null && !visibility.isBefore(equinoxToUse.minusDays(5))) {
                        firstMonthNewMoon = visibility
                        break
                    }
                }
                checkDate = checkDate.plusDays(1)
            }
            
            if (firstMonthNewMoon == null) {
                // Fallback: use equinox date as approximate month 1 start
                firstMonthNewMoon = equinoxToUse
            }
            
            // Count how many new moons have occurred since the first month
            // Calculate days between first month new moon and our new moon date
            val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(firstMonthNewMoon, newMoonDate)
            
            // Estimate month number (each month is approximately 29-30 days)
            // Add 1 because we're counting from month 1
            val estimatedMonth = (daysBetween / 29.5).toInt() + 1
            
            // Clamp to valid range (1-13)
            estimatedMonth.coerceIn(1, 13)
        } catch (e: Exception) {
            null
        }
    }
}

