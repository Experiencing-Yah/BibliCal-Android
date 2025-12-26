package com.example.biblicalmonth.data

import android.content.Context
import com.example.biblicalmonth.data.db.BiblicalMonthDatabase
import com.example.biblicalmonth.data.db.MonthStartEntity
import com.example.biblicalmonth.data.db.YearDecisionEntity
import com.example.biblicalmonth.data.settings.FirstfruitsRule
import com.example.biblicalmonth.data.settings.SettingsRepository
import com.example.biblicalmonth.domain.FeastDay
import com.example.biblicalmonth.domain.LunarDate
import com.example.biblicalmonth.domain.LunarMonth
import com.example.biblicalmonth.domain.MonthStatus
import java.time.LocalDate
import java.time.DayOfWeek
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.first

class LunarRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = BiblicalMonthDatabase.get(context).dao()
    private val settings = SettingsRepository(appContext)

    suspend fun hasAnyAnchor(): Boolean = dao.getAllMonthStarts().isNotEmpty()

    /**
     * Calculate default biblical year based on current Gregorian date.
     * Returns 4000 + current AD year if after biblical new year (Jan 1), or 3999 if before.
     * Actually, we need to check if we're after the biblical new year (Month 1 Day 1).
     * For simplicity, we'll use: if after Jan 1, use 4000 + current year, else 3999 + current year.
     */
    fun calculateDefaultYear(): Int {
        val now = LocalDate.now()
        val currentYear = now.year
        // If it's Jan 1, use 3999, otherwise use 4000
        // Actually, the user wants: 4000 + current year if after first day of biblical new year until Dec 31,
        // or 3999 if Jan 1 until end of biblical year.
        // Since we don't know the biblical new year yet, we'll default to 4000 + current year for now.
        // The user can adjust this manually.
        return if (now.monthValue == 1 && now.dayOfMonth == 1) {
            3999 + currentYear
        } else {
            4000 + currentYear
        }
    }

    suspend fun setAnchor(year: Int, month: Int, startDate: LocalDate) {
        dao.upsertMonthStart(
            MonthStartEntity(
                yearNumber = year,
                monthNumber = month,
                startEpochDay = startDate.toEpochDay(),
                confirmed = true,
            )
        )
    }

    suspend fun getToday(): LunarDate? = resolveFor(LocalDate.now())

    suspend fun resolveFor(date: LocalDate): LunarDate? {
        val epochDay = date.toEpochDay()
        val allStarts = dao.getAllMonthStarts().sortedBy { it.startEpochDay }
        if (allStarts.isEmpty()) return null

        val starting = allStarts.lastOrNull { it.startEpochDay <= epochDay } ?: return null

        var currentYear = starting.yearNumber
        var currentMonth = starting.monthNumber
        var currentStart = LocalDate.ofEpochDay(starting.startEpochDay)

        // If the last confirmed month start is far in the past, we can project forward.
        // Put a reasonable ceiling so we never loop forever.
        repeat(48) {
            val nextConfirmed = allStarts.firstOrNull { it.startEpochDay > currentStart.toEpochDay() }
            val (length, status) = lengthAndStatus(currentYear, currentMonth, currentStart, nextConfirmed)
            val endExclusive = currentStart.plusDays(length.toLong())

            if (!date.isBefore(currentStart) && date.isBefore(endExclusive)) {
                val dayOfMonth = (epochDay - currentStart.toEpochDay()).toInt() + 1
                return LunarDate(
                    yearNumber = currentYear,
                    monthNumber = currentMonth,
                    dayOfMonth = dayOfMonth,
                    monthStatus = status,
                    monthStart = currentStart,
                )
            }

            // Move to next month
            currentStart = endExclusive
            val next = nextMonth(currentYear, currentMonth)
            currentYear = next.first
            currentMonth = next.second
        }

        return null
    }

    suspend fun getMonth(year: Int, month: Int): LunarMonth? {
        val allStarts = dao.getAllMonthStarts().sortedBy { it.startEpochDay }
        if (allStarts.isEmpty()) return null

        // Find the closest confirmed start at or before target month.
        val target = allStarts.firstOrNull { it.yearNumber == year && it.monthNumber == month }
        if (target != null) {
            val start = LocalDate.ofEpochDay(target.startEpochDay)
            val nextConfirmed = allStarts.firstOrNull { it.startEpochDay > target.startEpochDay }
            val (len, status) = lengthAndStatus(year, month, start, nextConfirmed)
            return LunarMonth(year, month, start, len, status)
        }

        // Project from nearest earlier confirmed month start.
        val seed = allStarts
            .filter { it.yearNumber < year || (it.yearNumber == year && it.monthNumber < month) }
            .maxByOrNull { it.startEpochDay }
            ?: allStarts.maxByOrNull { it.startEpochDay }!!

        var currentYear = seed.yearNumber
        var currentMonth = seed.monthNumber
        var currentStart = LocalDate.ofEpochDay(seed.startEpochDay)

        repeat(120) { // Increased from 80 to allow projecting further into the future
            val nextConfirmed = allStarts.firstOrNull { it.startEpochDay > currentStart.toEpochDay() }
            val (length, status) = lengthAndStatus(currentYear, currentMonth, currentStart, nextConfirmed)
            if (currentYear == year && currentMonth == month) {
                return LunarMonth(year, month, currentStart, length, status)
            }
            currentStart = currentStart.plusDays(length.toLong())
            val next = nextMonth(currentYear, currentMonth)
            currentYear = next.first
            currentMonth = next.second
        }
        return null
    }

    suspend fun setBarleyAvivDecision(year: Int, aviv: Boolean, decidedOn: LocalDate) {
        dao.upsertYearDecision(
            YearDecisionEntity(
                yearNumber = year,
                barleyAviv = aviv,
                decidedEpochDay = decidedOn.toEpochDay(),
            )
        )
    }

    suspend fun getBarleyDecision(year: Int): Boolean? = dao.getYearDecision(year)?.barleyAviv

    suspend fun startNextMonthOn(startDate: LocalDate): LunarMonth? {
        val prevDay = startDate.minusDays(1)
        val prev = resolveFor(prevDay) ?: return null

        val next = nextMonth(prev.yearNumber, prev.monthNumber)
        dao.upsertMonthStart(
            MonthStartEntity(
                yearNumber = next.first,
                monthNumber = next.second,
                startEpochDay = startDate.toEpochDay(),
                confirmed = true,
            )
        )
        
        // When a month is confirmed, recalculate projections for future months
        // Remove stored projection for this month since it's now confirmed
        settings.setProjectedMonthLength(next.first, next.second, null)
        
        // Recalculate projections for subsequent months
        // This will happen automatically when getMonth is called, but we can trigger it here
        // by getting the next projected month
        val nextProjected = getMonth(next.first, next.second)
        if (nextProjected != null && nextProjected.status == MonthStatus.PROJECTED) {
            // Force recalculation by getting the month (which will call projectedLength)
            getMonth(nextProjected.yearNumber, nextProjected.monthNumber)
        }

        return getMonth(next.first, next.second)
    }

    suspend fun feastDaysForYear(year: Int): List<FeastDay> {
        val month1 = getMonth(year, 1) ?: return emptyList()
        val includeHanukkah = settings.includeHanukkah.first()
        val includePurim = settings.includePurim.first()

        fun atMonth1(day: Int, title: String): FeastDay {
            val date = month1.startDate.plusDays((day - 1).toLong())
            return FeastDay(title, date, year, 1, day)
        }

        // Firstfruits is always the day after the weekly Shabbat during Unleavened Bread (days 16-22)
        // Find the first Saturday during days 16-22, then Firstfruits is the next day (Sunday)
        val unleavenedBreadStart = month1.startDate.plusDays(14) // Day 15 (1-indexed)
        val firstfruitsDate = (0..6).asSequence()
            .map { unleavenedBreadStart.plusDays(it.toLong()) }
            .firstOrNull { it.dayOfWeek == DayOfWeek.SATURDAY }
            ?.plusDays(1) // Day after Shabbat
            ?: unleavenedBreadStart.plusDays(1) // Fallback to day 16 if no Saturday found
        val shavuotDate = firstfruitsDate.plusDays(49)

        val feasts = buildList {
            add(atMonth1(14, "Passover (14/1)"))
            add(atMonth1(15, "Unleavened Bread begins (15/1)"))
            add(atMonth1(21, "Unleavened Bread ends (21/1)"))
            add(FeastDay("Firstfruits", firstfruitsDate, year, 1, 0))
            add(FeastDay("Shavuot (count +50)", shavuotDate, year, 0, 0))
            // Fall feasts (assuming month 7 projected exists)
            getMonth(year, 7)?.let { m7 ->
                add(FeastDay("Trumpets (1/7)", m7.startDate, year, 7, 1))
                add(FeastDay("Atonement (10/7)", m7.startDate.plusDays(9), year, 7, 10))
                add(FeastDay("Tabernacles begins (15/7)", m7.startDate.plusDays(14), year, 7, 15))
                add(FeastDay("Tabernacles ends (22/7)", m7.startDate.plusDays(21), year, 7, 22))
            }
            
            // Optional holidays
            if (includeHanukkah) {
                getMonth(year, 9)?.let { m9 ->
                    // Hanukkah starts on 25th of 9th month (Kislev) and lasts 8 days
                    val hanukkahStart = m9.startDate.plusDays(24) // Day 25 (1-indexed)
                    for (i in 0..7) {
                        val dayNum = 25 + i
                        val title = if (i == 0) "Hanukkah begins (25/9)" 
                                   else if (i == 7) "Hanukkah ends (2/10)"
                                   else "Hanukkah (${dayNum}/9)"
                        add(FeastDay(title, hanukkahStart.plusDays(i.toLong()), year, 9, dayNum))
                    }
                }
            }
            
            if (includePurim) {
                // Purim is on 14th of 12th month (Adar)
                getMonth(year, 12)?.let { m12 ->
                    val purimDate = m12.startDate.plusDays(13) // Day 14 (1-indexed)
                    add(FeastDay("Purim (14/12)", purimDate, year, 12, 14))
                }
            }
        }

        return feasts.sortedBy { it.date }
    }

    suspend fun monthsForYear(year: Int): List<LunarMonth> {
        val month1 = getMonth(year, 1) ?: return emptyList()
        val months = mutableListOf<LunarMonth>()
        months.add(month1)
        for (m in 2..12) {
            getMonth(year, m)?.let { months.add(it) }
        }
        if (getBarleyDecision(year) == false) {
            getMonth(year, 13)?.let { months.add(it) }
        }
        return months
    }

    /**
     * Get list of (year, month) pairs for all projected months in a given year
     */
    suspend fun getProjectedMonthsForYear(year: Int): List<Pair<Int, Int>> {
        val months = monthsForYear(year)
        return months
            .filter { it.status == MonthStatus.PROJECTED }
            .map { year to it.monthNumber }
    }

    suspend fun hasMonthStartOn(date: LocalDate): Boolean {
        val epoch = date.toEpochDay()
        return dao.getAllMonthStarts().any { it.startEpochDay == epoch }
    }

    private suspend fun nextMonth(year: Int, month: Int): Pair<Int, Int> {
        return when (month) {
            12 -> {
                val decision = dao.getYearDecision(year)?.barleyAviv
                // Only check projectExtraMonth for the current year being processed
                // Don't carry it over to next year
                val projectExtraMonth = settings.projectExtraMonth.first()
                // If projecting extra month for THIS year OR barley decision says no, go to 13
                if (decision == false || (projectExtraMonth && month == 12)) {
                    year to 13
                } else {
                    (year + 1) to 1
                }
            }
            13 -> (year + 1) to 1
            else -> year to (month + 1)
        }
    }

    private suspend fun lengthAndStatus(
        year: Int,
        month: Int,
        currentStart: LocalDate,
        nextConfirmed: MonthStartEntity?,
    ): Pair<Int, MonthStatus> {
        if (nextConfirmed != null) {
            val delta = (nextConfirmed.startEpochDay - currentStart.toEpochDay()).toInt().absoluteValue
            if (delta in 1..30) {
                return delta to MonthStatus.CONFIRMED
            }
        }
        return projectedLength(year, month, currentStart) to MonthStatus.PROJECTED
    }

    private suspend fun projectedLength(year: Int, month: Int, currentStart: LocalDate): Int {
        // First check if user has specified a length for this month
        val userLength = settings.getProjectedMonthLength(year, month)
        if (userLength != null) {
            return userLength
        }
        
        // Otherwise use astronomical prediction
        val allStarts = dao.getAllMonthStarts().sortedBy { it.startEpochDay }
        val confirmedMonths = allStarts
            .filter { it.startEpochDay < currentStart.toEpochDay() }
            .takeLast(12) // Get last 12 confirmed months for pattern analysis
            .map { entity ->
                val start = LocalDate.ofEpochDay(entity.startEpochDay)
                val nextConfirmed = allStarts.firstOrNull { it.startEpochDay > entity.startEpochDay }
                val length = if (nextConfirmed != null) {
                    val delta = (nextConfirmed.startEpochDay - entity.startEpochDay).toInt().absoluteValue
                    if (delta in 1..30) delta else null
                } else {
                    null
                }
                if (length != null) {
                    Triple(entity.yearNumber, entity.monthNumber, length)
                } else {
                    null
                }
            }
            .filterNotNull()
            .map { (_, _, length) -> length }
        
        val predicted = predictMonthLengthAstronomically(year, month, confirmedMonths)
        
        // Store the predicted length so it persists
        settings.setProjectedMonthLength(year, month, predicted)
        
        return predicted
    }
    
    /**
     * Get the projected length for a month (for UI display)
     */
    suspend fun getProjectedLengthForMonth(year: Int, month: Int): Int? {
        return settings.getProjectedMonthLength(year, month)
    }

    private fun predictMonthLengthAstronomically(
        year: Int,
        month: Int,
        confirmedLengths: List<Int>
    ): Int {
        if (confirmedLengths.isEmpty()) {
            // No data: use simple alternating pattern
            return if (month % 2 == 1) 30 else 29
        }
        
        if (confirmedLengths.size == 1) {
            // Only one data point: alternate from it
            val lastLength = confirmedLengths.last()
            return if (lastLength == 29) 30 else 29
        }
        
        // Analyze pattern from confirmed months
        val lastFew = confirmedLengths.takeLast(6) // Look at last 6 months
        
        // Check for alternating pattern (29-30-29-30 or 30-29-30-29)
        if (lastFew.size >= 2) {
            val pattern = lastFew.takeLast(2)
            val isAlternating = pattern[0] != pattern[1]
            if (isAlternating) {
                // Continue alternating pattern
                return if (pattern.last() == 29) 30 else 29
            }
        }
        
        // Check for repeating pattern (e.g., 29-29-30 or 30-30-29)
        if (lastFew.size >= 3) {
            val lastThree = lastFew.takeLast(3)
            if (lastThree[0] == lastThree[1] && lastThree[1] != lastThree[2]) {
                // Pattern like 29-29-30, predict the opposite of the last
                return if (lastThree.last() == 29) 30 else 29
            }
        }
        
        // Calculate average synodic month length
        val average = confirmedLengths.average()
        
        // Synodic month averages ~29.53 days
        // If average is close to 29.5, alternate
        // If average < 29.5, favor 29
        // If average > 29.5, favor 30
        return when {
            average < 29.4 -> 29
            average > 29.6 -> 30
            else -> {
                // Close to average: alternate from last
                if (confirmedLengths.last() == 29) 30 else 29
            }
        }
    }
}

