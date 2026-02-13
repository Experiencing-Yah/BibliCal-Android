package com.experiencingyah.bibliCal.integrations

import android.content.Context
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.data.MonthStartSummary
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import com.experiencingyah.bibliCal.domain.LunarDate
import com.experiencingyah.bibliCal.util.SunsetCalculator
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first

object PassagesIntegration {
    private const val maxWeekIndex = 54
    private val parshaWeekOrder = listOf(
        "Bereshit", "Noach", "Lech-Lecha", "Vayera", "Chayei Sarah", "Toldot",
        "Vayetzei", "Vayishlach", "Vayeshev", "Mikeitz", "Vayigash", "Vayechi",
        "Shemot", "Vaera", "Bo", "Beshalach", "Yitro", "Mishpatim", "Terumah",
        "Tetzaveh", "Ki Tisa", "Vayakhel", "Pekudei", "Vayikra", "Tzav",
        "Shemini", "Tazria", "Metzora", "Acharei Mot", "Kedoshim", "Emor",
        "Behar", "Bechukotai", "Bamidbar", "Naso", "Behaalotecha", "Shelach",
        "Korach", "Chukat", "Balak", "Pinchas", "Matot", "Masei", "Devarim",
        "Vaetchanan", "Eikev", "Reeh", "Shoftim", "Ki Teitzei", "Ki Tavo",
        "Nitzavim", "Vayelech", "Haazinu", "Vezot Haberakhah",
    )

    suspend fun getCurrentParshaWeekIndex(context: Context): Int? {
        val repo = LunarRepository(context)
        val settings = SettingsRepository(context)
        val dateToUse = biblicalDateForNow(settings)
        val today = repo.resolveFor(dateToUse) ?: return null
        val weekIndex = parshaWeekIndex(today, dateToUse, repo, settings)
        return weekIndex.coerceIn(1, maxWeekIndex)
    }

    fun getParshaName(weekIndex: Int): String {
        if (weekIndex < 1 || weekIndex > parshaWeekOrder.size) {
            return "Week $weekIndex"
        }
        return parshaWeekOrder[weekIndex - 1]
    }

    suspend fun getCurrentHas13thMonth(context: Context): Boolean? {
        val repo = LunarRepository(context)
        val settings = SettingsRepository(context)
        val dateToUse = biblicalDateForNow(settings)
        val today = repo.resolveFor(dateToUse) ?: return null
        return repo.getBarleyDecision(today.yearNumber) == false
    }

    suspend fun hasAnyAnchor(context: Context): Boolean {
        val repo = LunarRepository(context)
        return repo.hasAnyAnchor()
    }

    suspend fun getMonthStartSummary(context: Context): MonthStartSummary {
        val repo = LunarRepository(context)
        return repo.getMonthStartSummary()
    }

    private suspend fun biblicalDateForNow(settings: SettingsRepository): LocalDate {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val today = now.toLocalDate()
        val cachedLocation = settings.getCachedLocation()
        val (lat, lon) = cachedLocation ?: (40.0 to -74.0)
        val sunset = SunsetCalculator.calculateSunsetTime(today, lat, lon, now.zone)
        return if (sunset != null && now.isAfter(sunset)) today.plusDays(1) else today
    }

    private suspend fun parshaWeekIndex(
        today: LunarDate,
        todayDate: LocalDate,
        repo: LunarRepository,
        settings: SettingsRepository
    ): Int {
        val currentYear = today.yearNumber
        val anchorDate = anchorDateForYear(currentYear, repo)

        val startAnchor = if (anchorDate != null && !todayDate.isBefore(anchorDate)) {
            anchorDate
        } else {
            val previousYear = currentYear - 1
            anchorDateForYear(previousYear, repo)
        } ?: run {
            // Fallback to day-of-year math if we cannot determine month 7
            val todayDayOfYear = dayOfYearFor(currentYear, today.monthNumber, today.dayOfMonth, repo, settings)
            val targetDayOfYear = dayOfYearFor(currentYear, 7, 22, repo, settings)
            val daysSince = if (todayDayOfYear >= targetDayOfYear) {
                todayDayOfYear - targetDayOfYear
            } else {
                val previousYear = currentYear - 1
                val daysInPreviousYear = totalDaysInYear(previousYear, repo, settings)
                val targetPrevYear = dayOfYearFor(previousYear, 7, 22, repo, settings)
                (daysInPreviousYear - targetPrevYear) + todayDayOfYear
            }
            val daysSinceLastShabbat = ((todayDate.dayOfWeek.value % 7) + 1) % 7
            val adjustedDays = daysSince - daysSinceLastShabbat
            return (adjustedDays / 7) + 1
        }

        val daysSince = ChronoUnit.DAYS.between(startAnchor, todayDate).toInt()
        val daysSinceLastShabbat = ((todayDate.dayOfWeek.value % 7) + 1) % 7
        val adjustedDays = daysSince - daysSinceLastShabbat
        return (adjustedDays / 7) + 1
    }

    private suspend fun anchorDateForYear(
        year: Int,
        repo: LunarRepository
    ): LocalDate? {
        val month7 = repo.getMonth(year, 7) ?: return null
        return month7.startDate.plusDays(21) // 7/22
    }

    private suspend fun dayOfYearFor(
        year: Int,
        monthNumber: Int,
        dayOfMonth: Int,
        repo: LunarRepository,
        settings: SettingsRepository
    ): Int {
        var dayOfYear = dayOfMonth
        if (monthNumber > 1) {
            for (month in 1 until monthNumber) {
                dayOfYear += monthLength(year, month, repo, settings)
            }
        }
        return dayOfYear
    }

    private suspend fun totalDaysInYear(
        year: Int,
        repo: LunarRepository,
        settings: SettingsRepository
    ): Int {
        var total = 0
        for (month in 1..12) {
            total += monthLength(year, month, repo, settings)
        }
        val decision = repo.getBarleyDecision(year)
        val projectExtraMonth = settings.projectExtraMonth.first()
        if (decision == false || (decision == null && projectExtraMonth)) {
            total += monthLength(year, 13, repo, settings)
        }
        return total
    }

    private suspend fun monthLength(
        year: Int,
        month: Int,
        repo: LunarRepository,
        settings: SettingsRepository
    ): Int {
        val lunarMonth = repo.getMonth(year, month)
        if (lunarMonth != null) {
            return lunarMonth.lengthDays
        }
        val projected = settings.getProjectedMonthLength(year, month)
        return projected ?: if (month % 2 == 1) 30 else 29
    }
}
