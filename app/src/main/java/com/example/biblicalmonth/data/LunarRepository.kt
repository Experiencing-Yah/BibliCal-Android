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

        repeat(80) {
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

        return getMonth(next.first, next.second)
    }

    suspend fun feastDaysForYear(year: Int): List<FeastDay> {
        val month1 = getMonth(year, 1) ?: return emptyList()

        fun atMonth1(day: Int, title: String): FeastDay {
            val date = month1.startDate.plusDays((day - 1).toLong())
            return FeastDay(title, date, year, 1, day)
        }

        val firstfruitsDate = when (settings.firstfruitsRule.first()) {
            FirstfruitsRule.FIXED_DAY_16 -> month1.startDate.plusDays(15)
            FirstfruitsRule.SUNDAY_DURING_UNLEAVENED_BREAD -> {
                // MVP: pick the first Sunday from days 16..22 (inclusive).
                val startSearch = month1.startDate.plusDays(15)
                (0..6).asSequence()
                    .map { startSearch.plusDays(it.toLong()) }
                    .firstOrNull { it.dayOfWeek == DayOfWeek.SUNDAY }
                    ?: startSearch
            }
        }
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

    suspend fun hasMonthStartOn(date: LocalDate): Boolean {
        val epoch = date.toEpochDay()
        return dao.getAllMonthStarts().any { it.startEpochDay == epoch }
    }

    private suspend fun nextMonth(year: Int, month: Int): Pair<Int, Int> {
        return when (month) {
            12 -> {
                val decision = dao.getYearDecision(year)?.barleyAviv
                if (decision == false) {
                    year to 13
                } else {
                    (year + 1) to 1
                }
            }
            13 -> (year + 1) to 1
            else -> year to (month + 1)
        }
    }

    private fun lengthAndStatus(
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
        return projectedLength(year, month) to MonthStatus.PROJECTED
    }

    private fun projectedLength(@Suppress("UNUSED_PARAMETER") year: Int, month: Int): Int {
        // MVP projection: alternate 30/29 by month number parity.
        // Users will still confirm starts; this is just for future-looking calendar.
        return if (month % 2 == 1) 30 else 29
    }
}

