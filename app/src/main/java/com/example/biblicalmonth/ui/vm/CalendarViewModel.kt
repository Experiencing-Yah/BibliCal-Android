package com.example.biblicalmonth.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.biblicalmonth.data.LunarRepository
import com.example.biblicalmonth.data.settings.SettingsRepository
import com.example.biblicalmonth.domain.FeastDay
import com.example.biblicalmonth.domain.LunarMonth
import com.example.biblicalmonth.domain.MonthStatus
import com.example.biblicalmonth.util.MonthNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DayCell(
    val gregorianDate: LocalDate,
    val lunarDay: Int,
    val isToday: Boolean,
    val feastTitles: List<String>,
)

data class CalendarUiState(
    val title: String = "Calendar",
    val subtitle: String? = null,
    val month: LunarMonth? = null,
    val weeks: List<List<DayCell?>> = emptyList(), // 7 columns; null = leading/trailing blanks
)

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LunarRepository(app)
    private val settings = SettingsRepository(app)

    private var selectedYear: Int? = null
    private var selectedMonth: Int? = null

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

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
    }

    fun nextMonth() {
        viewModelScope.launch {
            val y = selectedYear ?: return@launch
            val m = selectedMonth ?: return@launch
            val (ny, nm) = when (m) {
                12 -> {
                    if (repo.getBarleyDecision(y) == false) y to 13 else (y + 1) to 1
                }
                13 -> (y + 1) to 1
                else -> y to (m + 1)
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
            val title = "${MonthNames.format(month.monthNumber, namingMode)} — Year ${month.yearNumber}"
            val subtitle = if (month.status == MonthStatus.PROJECTED) "Projected month (confirm starts as you observe)" else null

            val feasts = repo.feastDaysForYear(y)
            val feastByDate = feasts.groupBy { it.date }.mapValues { it.value.map(FeastDay::title) }

            val cells = (0 until month.lengthDays).map { offset ->
                val g = month.startDate.plusDays(offset.toLong())
                DayCell(
                    gregorianDate = g,
                    lunarDay = offset + 1,
                    isToday = g == LocalDate.now(),
                    feastTitles = feastByDate[g].orEmpty(),
                )
            }

            _state.value = CalendarUiState(
                title = title,
                subtitle = subtitle,
                month = month,
                weeks = toWeeks(month.startDate, cells),
            )
        }
    }

    private fun toWeeks(start: LocalDate, days: List<DayCell>): List<List<DayCell?>> {
        // Week starts Sunday.
        val startDow = start.dayOfWeek
        val leadingBlanks = ((startDow.value % 7) /*Mon=1..Sun=7 -> Sun=0*/).let { if (it == 0) 0 else it }
        val padded = mutableListOf<DayCell?>().apply {
            repeat(leadingBlanks) { add(null) }
            addAll(days)
        }
        val trailing = (7 - (padded.size % 7)).let { if (it == 7) 0 else it }
        repeat(trailing) { padded.add(null) }
        return padded.chunked(7)
    }
}

