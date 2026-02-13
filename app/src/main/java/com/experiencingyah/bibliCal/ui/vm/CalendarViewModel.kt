package com.experiencingyah.bibliCal.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import com.experiencingyah.bibliCal.domain.FeastDay
import com.experiencingyah.bibliCal.domain.LunarMonth
import com.experiencingyah.bibliCal.domain.MonthStatus
import com.experiencingyah.bibliCal.util.MonthNames
import com.experiencingyah.bibliCal.util.SunsetCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DayCell(
    val gregorianDate: LocalDate,
    val lunarDay: Int,
    val isToday: Boolean,
    val feastTitles: List<String>,
)

data class ProjectedMonthInfo(
    val year: Int,
    val month: Int,
    val monthName: String,
)

data class CalendarUiState(
    val title: String = "Calendar",
    val subtitle: String? = null,
    val month: LunarMonth? = null,
    val weeks: List<List<DayCell?>> = emptyList(), // 7 columns; null = leading/trailing blanks
    val feastsInMonth: List<FeastDay> = emptyList(), // Feasts that fall within the displayed month
    val projectExtraMonth: Boolean = false,
    val projectedMonths: Map<Pair<Int, Int>, Int> = emptyMap(), // (year, month) -> 29 or 30
    val projectedMonthInfos: List<ProjectedMonthInfo> = emptyList(), // List of projected months with names
    val currentMonthProjectedLength: Int? = null, // Projected length for current month (29 or 30)
)

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LunarRepository(app)
    private val settings = SettingsRepository(app)

    private var selectedYear: Int? = null
    private var selectedMonth: Int? = null
    private var cachedLocation: Pair<Double, Double>? = null

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()
    
    init {
        // Load cached location immediately so we can calculate sunset right away
        viewModelScope.launch {
            try {
                cachedLocation = settings.getCachedLocation()
            } catch (e: Exception) {
                // Ignore errors loading cached location
            }
        }
    }

    init {
        viewModelScope.launch {
            val today = repo.getToday()
            if (today != null) {
                selectedYear = today.yearNumber
                selectedMonth = today.monthNumber
                load()
            } else {
                _state.value = CalendarUiState(
                    title = "Calendar",
                    subtitle = "Set an anchor on the Today tab to begin.",
                )
            }
        }
        
        // Refresh when settings change (for Hanukkah/Purim toggles)
        viewModelScope.launch {
            settings.includeHanukkah.collect {
                refresh()
            }
        }
        viewModelScope.launch {
            settings.includePurim.collect {
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val today = repo.getToday()
            if (today != null) {
                // Update selected month/year if they're not set or if today changed
                if (selectedYear == null || selectedMonth == null) {
                    selectedYear = today.yearNumber
                    selectedMonth = today.monthNumber
                }
                load()
            } else {
                _state.value = CalendarUiState(
                    title = "Calendar",
                    subtitle = "Set an anchor on the Today tab to begin.",
                )
            }
        }
    }

    fun nextMonth() {
        viewModelScope.launch {
            val y = selectedYear ?: return@launch
            val m = selectedMonth ?: return@launch
            val projectExtraMonth = settings.projectExtraMonth.first()
            val (ny, nm) = when (m) {
                12 -> {
                    // Check if projecting extra month for THIS year OR if barley decision says no
                    if (repo.getBarleyDecision(y) == false) {
                        y to 13
                    } else if (projectExtraMonth) {
                        y to 13
                    } else {
                        (y + 1) to 1
                    }
                }
                13 -> (y + 1) to 1
                else -> y to (m + 1)
            }
            // Check if the next month has data before navigating
            val nextMonthData = repo.getMonth(ny, nm)
            if (nextMonthData == null) {
                // Don't navigate if no data exists
                return@launch
            }
            selectedYear = ny
            selectedMonth = nm
            load()
        }
    }

    fun prevMonth() {
        viewModelScope.launch {
            val y = selectedYear ?: return@launch
            val m = selectedMonth ?: return@launch
            val (py, pm) = when (m) {
                1 -> {
                    val prevYear = (y - 1).coerceAtLeast(1)
                    val lastMonth = if (repo.getBarleyDecision(prevYear) == false) 13 else 12
                    prevYear to lastMonth
                }
                else -> y to (m - 1)
            }
            // Check if the previous month has data before navigating
            val prevMonthData = repo.getMonth(py, pm)
            if (prevMonthData == null) {
                // Don't navigate if no data exists
                return@launch
            }
            selectedYear = py
            selectedMonth = pm
            load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            val y = selectedYear ?: return@launch
            val m = selectedMonth ?: return@launch
            val month = repo.getMonth(y, m)
            if (month == null) {
                _state.value = CalendarUiState(
                    title = "Calendar",
                    subtitle = "Missing month data; try setting an anchor.",
                )
                return@launch
            }

            val namingMode = settings.monthNamingMode.first()
            val projectExtraMonth = settings.projectExtraMonth.first()
            val title = "${MonthNames.format(month.monthNumber, namingMode)} Month — Year ${month.yearNumber}"

            // Get projected month lengths for current year
            val projectedMonthsForYear = settings.getProjectedMonthsForYear(y)
            val projectedMonthsMap = projectedMonthsForYear.mapKeys { (monthNum, _) -> y to monthNum }

            // Only show projection checkbox for the current month being viewed (if it's projected)
            val projectedMonthInfos = if (month.status == MonthStatus.PROJECTED) {
                val monthName = MonthNames.format(month.monthNumber, namingMode)
                listOf(ProjectedMonthInfo(y, m, monthName))
            } else {
                emptyList()
            }

            // Apply projection adjustments
            var adjustedMonth = month
            val userProjectedLength = projectedMonthsMap[y to m]
            if (userProjectedLength != null && month.status == MonthStatus.PROJECTED) {
                // User has specified a length for this projected month
                adjustedMonth = month.copy(lengthDays = userProjectedLength)
            } else if (projectExtraMonth && m == 12) {
                // If projecting extra month, treat year as having 13 months (keep original length)
                // No adjustment needed here as it's handled in getMonth
            }

            // Gregorian month range for subtitle (matching iOS)
            val monthStart = adjustedMonth.startDate
            val monthEnd = monthStart.plusDays((adjustedMonth.lengthDays - 1).toLong())
            val monthFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())
            val startMonthName = monthStart.format(monthFormatter)
            val endMonthName = monthEnd.format(monthFormatter)
            val subtitle = if (startMonthName == endMonthName) startMonthName else "$startMonthName - $endMonthName"
            
            // Get the projected length for the current month (from repository's prediction or user setting)
            // This will be used to set the checkbox state
            val currentProjectedLength = if (month.status == MonthStatus.PROJECTED) {
                userProjectedLength ?: repo.getProjectedLengthForMonth(y, m)
            } else {
                null
            }

            var feasts = repo.feastDaysForYear(y)
            // Fallback: when viewing month 12 with Purim enabled, ensure Purim is included
            // (repository may omit it if month 1 is missing for that year)
            if (m == 12 && settings.includePurim.first() && !feasts.any { it.title.contains("Purim") }) {
                val purimDate = adjustedMonth.startDate.plusDays(13)
                feasts = feasts + FeastDay("Purim (14/12)", purimDate, y, 12, 14)
            }
            val feastByDate = feasts.groupBy { it.date }.mapValues { it.value.map(FeastDay::title) }
            
            // Filter feasts that fall within this month
            val feastsInMonth = feasts.filter { it.date >= monthStart && it.date <= monthEnd }
                .map { feast ->
                    // For feasts with dayOfMonth 0, calculate the actual day from the month start
                    if (feast.dayOfMonth == 0) {
                        // Calculate days from the month start date
                        val daysFromStart = (java.time.temporal.ChronoUnit.DAYS.between(monthStart, feast.date) + 1).toInt()
                        if (daysFromStart > 0 && daysFromStart <= 30) {
                            feast.copy(dayOfMonth = daysFromStart)
                        } else {
                            // If not in current month, try to get the actual month
                            val actualMonth = repo.getMonth(feast.yearNumber, feast.monthNumber)
                            if (actualMonth != null && feast.date >= actualMonth.startDate) {
                                val daysFromActualStart = (java.time.temporal.ChronoUnit.DAYS.between(actualMonth.startDate, feast.date) + 1).toInt()
                                if (daysFromActualStart > 0 && daysFromActualStart <= 30) {
                                    feast.copy(dayOfMonth = daysFromActualStart)
                                } else {
                                    feast
                                }
                            } else {
                                feast
                            }
                        }
                    } else {
                        feast
                    }
                }

            // Determine which Gregorian date corresponds to "today" based on sunset
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            val todayDate = LocalDate.now()
            val dateToUseForToday = if (cachedLocation != null) {
                val zoneId = ZoneId.systemDefault()
                val todaySunset = SunsetCalculator.calculateSunsetTime(
                    todayDate,
                    cachedLocation!!.first,
                    cachedLocation!!.second,
                    zoneId
                )
                
                if (todaySunset != null && now.isAfter(todaySunset)) {
                    // After sunset: current biblical day corresponds to tomorrow's Gregorian date
                    todayDate.plusDays(1)
                } else {
                    // Before sunset: current biblical day corresponds to today's Gregorian date
                    todayDate
                }
            } else {
                // No location - default to today
                todayDate
            }
            
            val cells = (0 until adjustedMonth.lengthDays).map { offset ->
                val g = adjustedMonth.startDate.plusDays(offset.toLong())
                DayCell(
                    gregorianDate = g,
                    lunarDay = offset + 1,
                    isToday = g == dateToUseForToday,
                    feastTitles = feastByDate[g].orEmpty(),
                )
            }

            _state.value = CalendarUiState(
                title = title,
                subtitle = subtitle,
                month = adjustedMonth,
                weeks = toWeeks(adjustedMonth.startDate, cells),
                feastsInMonth = feastsInMonth,
                projectExtraMonth = projectExtraMonth,
                projectedMonths = projectedMonthsMap,
                projectedMonthInfos = projectedMonthInfos,
                currentMonthProjectedLength = currentProjectedLength,
            )
        }
    }

    fun setProjectExtraMonth(enabled: Boolean) {
        viewModelScope.launch {
            settings.setProjectExtraMonth(enabled)
            load()
        }
    }

    fun setProjectedMonthLength(year: Int, month: Int, length: Int?) {
        viewModelScope.launch {
            if (length != null) {
                // When user manually sets a month length, cascade to all future months
                // using alternating 29/30 pattern starting from this month
                settings.cascadeProjectedMonthLengths(year, month, length)
            } else {
                // If removing a length, just remove this specific entry
                settings.setProjectedMonthLength(year, month, null)
            }
            load()
        }
    }

    private fun toWeeks(start: LocalDate, days: List<DayCell>): List<List<DayCell?>> {
        // Week starts with Day 1 (which corresponds to the start date's day of week)
        // We want Day 1 to always be in the first column
        val startDow = start.dayOfWeek
        // Convert to 0-based where 0 = Sunday (Day 1), 1 = Monday (Day 2), etc.
        val leadingBlanks = startDow.value % 7
        val padded = mutableListOf<DayCell?>().apply {
            repeat(leadingBlanks) { add(null) }
            addAll(days)
        }
        val trailing = (7 - (padded.size % 7)).let { if (it == 7) 0 else it }
        repeat(trailing) { padded.add(null) }
        return padded.chunked(7)
    }
}

