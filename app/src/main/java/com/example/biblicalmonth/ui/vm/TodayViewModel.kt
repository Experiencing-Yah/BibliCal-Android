package com.example.biblicalmonth.ui.vm

import android.app.Application
import android.location.Geocoder
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.biblicalmonth.data.LunarRepository
import com.example.biblicalmonth.data.settings.SettingsRepository
import com.example.biblicalmonth.domain.FeastDay
import com.example.biblicalmonth.domain.MonthStatus
import com.example.biblicalmonth.util.MonthNames
import com.example.biblicalmonth.util.SunsetCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class UpcomingFeast(
    val title: String,
    val date: LocalDate,
    val daysUntil: Long,
)

data class SunsetInfo(
    val nextSunset: ZonedDateTime?,
    val countdownText: String,
    val timeZone: String? = null,
    val location: String? = null, // Location string like "40.0°N, 74.0°W"
)

data class TodayUiState(
    val hasAnchor: Boolean = false,
    val lunarLabel: String = "—",
    val gregorianLabel: String = LocalDate.now().toString(),
    val gregorianDaytimeDate: String? = null,
    val gregorianSunsetDate: String? = null,
    val isAfterSunset: Boolean = false,
    val hint: String? = null,
    val sunsetInfo: SunsetInfo? = null,
    val upcomingFeasts: List<UpcomingFeast> = emptyList(),
    val currentDayOfMonth: Int = 0,
    val showNextMonthButton: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingSunset: Boolean = false,
    val isLoadingFeasts: Boolean = false,
)

class TodayViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LunarRepository(app)
    private val settings = SettingsRepository(app)
    private val geocoder = Geocoder(app)

    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    private var currentLocation: Location? = null

    init {
        refresh()
        // Update countdown every second
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000) // 1 second
                updateSunsetCountdown()
            }
        }
    }

    fun setLocation(location: Location?) {
        currentLocation = location
        // Cache location for widget use
        if (location != null) {
            viewModelScope.launch {
                settings.cacheLocation(location.latitude, location.longitude)
            }
        }
        updateSunsetCountdown()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            
            val hasAnchor = repo.hasAnyAnchor()
            if (!hasAnchor) {
                _state.value = _state.value.copy(
                    hasAnchor = false,
                    lunarLabel = "No anchor set",
                    hint = "Set an anchor (e.g., Month 1 Day 1) to begin tracking.",
                    isLoading = false,
                )
                updateUpcomingFeasts()
                return@launch
            }

            // Calculate sunset and daytime dates FIRST to determine which Gregorian date to use
            val location = currentLocation
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            val todayDate = LocalDate.now()
            val yesterdayDate = todayDate.minusDays(1)
            
            var daytimeDate: String? = null
            var sunsetDate: String? = null
            var isAfterSunset = false
            var dateToUseForBiblical = todayDate // Default to today
            
            if (location != null) {
                val zoneId = ZoneId.systemDefault()
                val todaySunset = SunsetCalculator.calculateSunsetTime(todayDate, location.latitude, location.longitude, zoneId)
                val yesterdaySunset = SunsetCalculator.calculateSunsetTime(yesterdayDate, location.latitude, location.longitude, zoneId)
                
                if (todaySunset != null) {
                    isAfterSunset = now.isAfter(todaySunset)
                    // Daytime date is always today (Gregorian date)
                    daytimeDate = todayDate.toString()
                    // Sunset date: if after today's sunset, use today, otherwise yesterday
                    // (Biblical day starts at sunset, so before today's sunset = still yesterday's biblical day)
                    sunsetDate = if (now.isAfter(todaySunset)) {
                        todayDate.toString()
                    } else {
                        yesterdayDate.toString()
                    }
                    // Use sunset date for biblical date calculation
                    dateToUseForBiblical = if (now.isAfter(todaySunset)) {
                        todayDate
                    } else {
                        yesterdayDate
                    }
                } else {
                    daytimeDate = todayDate.toString()
                    sunsetDate = yesterdayDate.toString()
                    dateToUseForBiblical = yesterdayDate
                }
            } else {
                daytimeDate = todayDate.toString()
                sunsetDate = yesterdayDate.toString()
                dateToUseForBiblical = yesterdayDate
            }

            // Get biblical date using the appropriate Gregorian date (sunset date if after sunset)
            val today = repo.resolveFor(dateToUseForBiblical)
            if (today == null) {
                _state.value = _state.value.copy(
                    hasAnchor = true,
                    lunarLabel = "Unable to compute",
                    hint = "Check that you have at least one month start saved.",
                )
                updateUpcomingFeasts()
                return@launch
            }

            // Format as "1st day of the 11th month, 6025"
            val dayOrdinal = when (today.dayOfMonth) {
                1 -> "1st"
                2 -> "2nd"
                3 -> "3rd"
                else -> "${today.dayOfMonth}th"
            }
            val monthOrdinal = when (today.monthNumber) {
                1 -> "1st"
                2 -> "2nd"
                3 -> "3rd"
                else -> "${today.monthNumber}th"
            }
            val lunarLabel = "$dayOrdinal day of the $monthOrdinal month, ${today.yearNumber}"

            _state.value = _state.value.copy(
                hasAnchor = true,
                lunarLabel = lunarLabel,
                gregorianLabel = LocalDate.now().toString(),
                gregorianDaytimeDate = daytimeDate,
                gregorianSunsetDate = sunsetDate,
                isAfterSunset = isAfterSunset,
                currentDayOfMonth = today.dayOfMonth,
                showNextMonthButton = today.dayOfMonth >= 28,
                hint = if (today.dayOfMonth == 29 || today.dayOfMonth == 30) {
                    "Day ${today.dayOfMonth}: you'll get a prompt to confirm if the new moon was seen."
                } else null,
                isLoading = false,
            )
            updateUpcomingFeasts()
            updateSunsetCountdown()
        }
    }

    private fun updateSunsetCountdown() {
        viewModelScope.launch {
            val location = currentLocation
            if (location == null) {
                _state.value = _state.value.copy(sunsetInfo = null, isLoadingSunset = false)
                return@launch
            }

            // Set loading state if we don't have sunset info yet
            if (_state.value.sunsetInfo == null) {
                _state.value = _state.value.copy(isLoadingSunset = true)
            }

            val zoneId = ZoneId.systemDefault()
            val nextSunset = SunsetCalculator.calculateNextSunset(
                latitude = location.latitude,
                longitude = location.longitude,
                timeZone = zoneId.id
            )

            if (nextSunset != null) {
                val now = ZonedDateTime.now(zoneId)
                val duration = Duration.between(now, nextSunset)
                val hours = duration.toHours()
                val minutes = (duration.toMinutes() % 60).toInt()
                val seconds = (duration.seconds % 60).toInt()

                val countdownText = if (duration.isNegative) {
                    "Sunset passed"
                } else {
                    String.format("%02d:%02d:%02d", hours, minutes, seconds)
                }
                
                // Get city name from location
                val locationStr = withContext(Dispatchers.IO) {
                    if (location != null) {
                        try {
                            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                val address = addresses[0]
                                // Try to get city name (locality), fallback to admin area, then country
                                address.locality ?: address.adminArea ?: address.countryName
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                }

                _state.value = _state.value.copy(
                    sunsetInfo = SunsetInfo(nextSunset, countdownText, zoneId.id, locationStr),
                    isLoadingSunset = false
                )
            } else {
                _state.value = _state.value.copy(sunsetInfo = null, isLoadingSunset = false)
            }
        }
    }

    private suspend fun updateUpcomingFeasts() {
        _state.value = _state.value.copy(isLoadingFeasts = true)
        
        val today = LocalDate.now()
        val endDate = today.plusDays(30)
        val feasts = mutableListOf<UpcomingFeast>()

        // Get feasts from current and next year
        val currentYear = repo.getToday()?.yearNumber
        if (currentYear != null) {
            val yearFeasts = repo.feastDaysForYear(currentYear)
            feasts.addAll(
                yearFeasts
                    .filter { it.date.isAfter(today.minusDays(1)) && it.date.isBefore(endDate.plusDays(1)) }
                    .map { UpcomingFeast(it.title, it.date, Duration.between(today.atStartOfDay(), it.date.atStartOfDay()).toDays()) }
            )

            // Also check next year
            val nextYearFeasts = repo.feastDaysForYear(currentYear + 1)
            feasts.addAll(
                nextYearFeasts
                    .filter { it.date.isBefore(endDate.plusDays(1)) }
                    .map { UpcomingFeast(it.title, it.date, Duration.between(today.atStartOfDay(), it.date.atStartOfDay()).toDays()) }
            )
        }

        // Add weekly Shabbat (Friday sunset to Saturday sunset)
        var checkDate = today
        while (checkDate.isBefore(endDate)) {
            // Find next Friday
            val daysUntilFriday = (DayOfWeek.FRIDAY.value - checkDate.dayOfWeek.value + 7) % 7
            val friday = if (daysUntilFriday == 0 && checkDate.dayOfWeek == DayOfWeek.FRIDAY) {
                checkDate
            } else {
                checkDate.plusDays(daysUntilFriday.toLong())
            }

            if (friday.isBefore(endDate.plusDays(1))) {
                val daysUntil = Duration.between(today.atStartOfDay(), friday.atStartOfDay()).toDays()
                feasts.add(UpcomingFeast("Shabbat", friday, daysUntil))
            }
            checkDate = friday.plusDays(7)
        }

        // Sort by date and remove duplicates
        val sortedFeasts = feasts
            .sortedBy { it.date }
            .distinctBy { "${it.title}-${it.date}" }
            .filter { it.daysUntil >= 0 }

        _state.value = _state.value.copy(upcomingFeasts = sortedFeasts, isLoadingFeasts = false)
    }

    fun setCurrentDate(year: Int, month: Int, day: Int, useSunsetDate: Boolean) {
        viewModelScope.launch {
            val location = currentLocation
            val zoneId = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zoneId)
            val todayDate = LocalDate.now()
            
            // Determine which Gregorian date to use
            val gregorianDate = if (useSunsetDate && location != null) {
                // Use sunset date: if after today's sunset, use today, otherwise yesterday
                val todaySunset = SunsetCalculator.calculateSunsetTime(todayDate, location.latitude, location.longitude, zoneId)
                if (todaySunset != null && now.isAfter(todaySunset)) {
                    todayDate
                } else {
                    todayDate.minusDays(1)
                }
            } else {
                // Use daytime date (today)
                todayDate
            }
            
            // Calculate month start: if day 4 occurs on gregorianDate, month started (day-1) days earlier
            val monthStart = gregorianDate.minusDays((day - 1).toLong())
            repo.setAnchor(year = year, month = month, startDate = monthStart)
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

