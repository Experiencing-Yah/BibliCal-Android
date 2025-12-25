package com.example.biblicalmonth.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.biblicalmonth.data.LunarRepository
import com.example.biblicalmonth.data.settings.FirstfruitsRule
import com.example.biblicalmonth.data.settings.MonthNamingMode
import com.example.biblicalmonth.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsUiState(
    val statusNotificationEnabled: Boolean = true,
    val promptsEnabled: Boolean = true,
    val monthNamingMode: MonthNamingMode = MonthNamingMode.ORDINAL,
    val firstfruitsRule: FirstfruitsRule = FirstfruitsRule.FIXED_DAY_16,
    val selectedCalendarId: Long = -1L,
    val hasAnchor: Boolean = false,
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsRepository(app)
    private val repo = LunarRepository(app)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = SettingsUiState(
                statusNotificationEnabled = settings.statusNotificationEnabled.first(),
                promptsEnabled = settings.promptsEnabled.first(),
                monthNamingMode = settings.monthNamingMode.first(),
                firstfruitsRule = settings.firstfruitsRule.first(),
                selectedCalendarId = settings.selectedCalendarId.first(),
                hasAnchor = repo.hasAnyAnchor(),
            )
        }
    }

    fun setStatusNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setStatusNotificationEnabled(enabled)
            refresh()
        }
    }

    fun setPromptsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setPromptsEnabled(enabled)
            refresh()
        }
    }

    fun setMonthNamingMode(mode: MonthNamingMode) {
        viewModelScope.launch {
            settings.setMonthNamingMode(mode)
            refresh()
        }
    }

    fun setSelectedCalendarId(id: Long) {
        viewModelScope.launch {
            settings.setSelectedCalendarId(id)
            refresh()
        }
    }

    fun setFirstfruitsRule(rule: FirstfruitsRule) {
        viewModelScope.launch {
            settings.setFirstfruitsRule(rule)
            refresh()
        }
    }

    suspend fun currentYearNumberOrNull(): Int? = repo.getToday()?.yearNumber
}

