package com.example.biblicalmonth.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.biblicalmonth.data.LunarRepository
import com.example.biblicalmonth.data.settings.SettingsRepository
import com.example.biblicalmonth.domain.MonthStatus
import com.example.biblicalmonth.util.MonthNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TodayUiState(
    val hasAnchor: Boolean = false,
    val lunarLabel: String = "—",
    val gregorianLabel: String = LocalDate.now().toString(),
    val hint: String? = null,
)

class TodayViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LunarRepository(app)
    private val settings = SettingsRepository(app)

    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val hasAnchor = repo.hasAnyAnchor()
            if (!hasAnchor) {
                _state.value = TodayUiState(
                    hasAnchor = false,
                    lunarLabel = "No anchor set",
                    hint = "Set an anchor (e.g., Month 1 Day 1) to begin tracking.",
                )
                return@launch
            }

            val today = repo.getToday()
            if (today == null) {
                _state.value = TodayUiState(
                    hasAnchor = true,
                    lunarLabel = "Unable to compute",
                    hint = "Check that you have at least one month start saved.",
                )
                return@launch
            }

            val mode = settings.monthNamingMode.first()
            val lunarLabel = "${MonthNames.format(today.monthNumber, mode)} ${today.dayOfMonth}, Year ${today.yearNumber}" +
                if (today.monthStatus == MonthStatus.PROJECTED) " (projected)" else ""

            _state.value = TodayUiState(
                hasAnchor = true,
                lunarLabel = lunarLabel,
                gregorianLabel = LocalDate.now().toString(),
                hint = if (today.dayOfMonth == 29 || today.dayOfMonth == 30) {
                    "Day ${today.dayOfMonth}: you’ll get a prompt to confirm if the new moon was seen."
                } else null
            )
        }
    }

    fun setAnchorToTodayMonth1Year1() {
        viewModelScope.launch {
            repo.setAnchor(year = 1, month = 1, startDate = LocalDate.now())
            refresh()
        }
    }

    fun confirmNextMonthStartsTomorrow() {
        viewModelScope.launch {
            repo.startNextMonthOn(LocalDate.now().plusDays(1))
            refresh()
        }
    }
}

