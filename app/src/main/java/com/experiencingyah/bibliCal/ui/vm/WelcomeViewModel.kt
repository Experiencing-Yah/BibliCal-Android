package com.experiencingyah.bibliCal.ui.vm

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import com.experiencingyah.bibliCal.util.NewMoonCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class WelcomeStep {
    KNOW_BIBLICAL_DATE,
    KNOW_NEW_MOON_DATE,
    ESTIMATE_FROM_LOCATION
}

data class WelcomeUiState(
    val currentStep: WelcomeStep = WelcomeStep.KNOW_BIBLICAL_DATE,
    val isLoadingLocation: Boolean = false,
    val isLoadingEstimate: Boolean = false,
    val estimatedNewMoonDate: LocalDate? = null,
    val estimatedMonth: Int? = null,
    val estimatedDay: Int? = null,
    val location: Location? = null,
    val errorMessage: String? = null,
)

class WelcomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LunarRepository(app)
    private val settings = SettingsRepository(app)
    
    private val _state = MutableStateFlow(WelcomeUiState())
    val state: StateFlow<WelcomeUiState> = _state.asStateFlow()
    
    fun setStep(step: WelcomeStep) {
        _state.value = _state.value.copy(currentStep = step, errorMessage = null)
        
        // If moving to estimate step, start location request
        if (step == WelcomeStep.ESTIMATE_FROM_LOCATION) {
            // Location will be set via setLocation() from the screen
            _state.value = _state.value.copy(isLoadingEstimate = true)
        }
    }
    
    fun setLocation(location: Location?) {
        _state.value = _state.value.copy(
            location = location,
            isLoadingLocation = false
        )
        if (location != null) {
            viewModelScope.launch {
                settings.cacheLocation(location.latitude, location.longitude)
            }
        }
        
        // If we have location and are on estimate step, calculate estimate
        if (location != null && _state.value.currentStep == WelcomeStep.ESTIMATE_FROM_LOCATION) {
            calculateNewMoonEstimate(location)
        }
    }
    
    fun setLoadingLocation(loading: Boolean) {
        _state.value = _state.value.copy(isLoadingLocation = loading)
    }
    
    private fun calculateNewMoonEstimate(location: Location) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingEstimate = true, errorMessage = null)
            
            try {
                // Find the most recent new moon visibility date
                val today = LocalDate.now()
                val estimatedDate = NewMoonCalculator.findMostRecentNewMoonVisibility(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    beforeOrOnDate = today
                )
                
                if (estimatedDate != null) {
                    // Estimate the biblical month based on equinox
                    val estimatedMonth = NewMoonCalculator.estimateBiblicalMonth(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        currentDate = today,
                        newMoonDate = estimatedDate
                    )
                    
                    // Calculate the biblical day: days since new moon visibility + 1
                    // (day 1 is the day the new moon was visible)
                    val daysSinceNewMoon = java.time.temporal.ChronoUnit.DAYS.between(estimatedDate, today)
                    val estimatedDay = (daysSinceNewMoon + 1).toInt().coerceIn(1, 30)
                    
                    _state.value = _state.value.copy(
                        estimatedNewMoonDate = estimatedDate,
                        estimatedMonth = estimatedMonth,
                        estimatedDay = estimatedDay,
                        isLoadingEstimate = false
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoadingEstimate = false,
                        errorMessage = "Unable to estimate new moon date. Please try setting the date manually."
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoadingEstimate = false,
                    errorMessage = "Error calculating estimate: ${e.message}"
                )
            }
        }
    }
    
    fun setBiblicalDate(
        year: Int,
        month: Int,
        day: Int,
        referenceDate: LocalDate,
    ): Job {
        return viewModelScope.launch {
            try {
                // Calculate month start: if day X occurs on dateToUse, month started (day-1) days earlier
                val monthStart = referenceDate.minusDays((day - 1).toLong())
                repo.setAnchor(year = year, month = month, startDate = monthStart)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = "Error setting date: ${e.message}"
                )
            }
        }
    }
    
    fun setNewMoonDate(newMoonDate: LocalDate, year: Int, month: Int): Job {
        return viewModelScope.launch {
            try {
                // The newMoonDate represents when the first sliver was visible (during daytime)
                // The first day of the month starts at sunset on that same date
                // So if first sliver visible on 21st (daytime), month starts at sunset 21st
                // The month start date = 21st represents sunset 21st to sunset 22nd
                // We use the date directly as the month start (not minus 1 day)
                repo.setAnchor(year = year, month = month, startDate = newMoonDate)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = "Error setting date: ${e.message}"
                )
            }
        }
    }
    
    fun useEstimatedDate(year: Int, month: Int, customDate: LocalDate? = null): Job? {
        val dateToUse = customDate ?: _state.value.estimatedNewMoonDate
        if (dateToUse != null) {
            return setNewMoonDate(dateToUse, year, month)
        }
        return null
    }
    
    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}

