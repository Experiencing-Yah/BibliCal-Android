package com.example.biblicalmonth.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.biblicalmonth.data.LunarRepository
import com.example.biblicalmonth.data.settings.SettingsRepository
import com.example.biblicalmonth.notifications.Notifier
import com.example.biblicalmonth.util.MonthNames
import com.example.biblicalmonth.util.SunsetCalculator
import com.example.biblicalmonth.widgets.TodayWidgetProvider
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class DailyTickWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repo = LunarRepository(applicationContext)
        val settings = SettingsRepository(applicationContext)

        Notifier.ensureChannels(applicationContext)

        val today = repo.getToday()
        if (today != null) {
            val namingMode = settings.monthNamingMode.first()
            val label = "${MonthNames.format(today.monthNumber, namingMode)} ${today.dayOfMonth}, Year ${today.yearNumber}"

            if (settings.statusNotificationEnabled.first()) {
                Notifier.showOrUpdateStatus(applicationContext, today, label)
            } else {
                Notifier.cancelStatus(applicationContext)
            }

            TodayWidgetProvider.updateAll(applicationContext)

            if (settings.promptsEnabled.first()) {
                maybePromptMoon(repo, settings, today)
                maybePromptBarley(repo, settings, today)
            }
            
            // Check for Shabbat and feast reminders
            checkShabbatReminder(repo, settings)
            checkFeastReminders(repo, settings)
        }

        return Result.success()
    }
    
    private suspend fun checkShabbatReminder(repo: LunarRepository, settings: SettingsRepository) {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val today = now.toLocalDate()
        
        // Get user's location
        val location = try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            val cancellationTokenSource = CancellationTokenSource()
            val locationTask = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.token
            )
            Tasks.await(locationTask, 5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            null
        }
        
        // Use actual location or fallback to default coordinates (40.0, -74.0)
        val latitude = location?.latitude ?: 40.0
        val longitude = location?.longitude ?: -74.0
        
        // Find next Friday
        val daysUntilFriday = (DayOfWeek.FRIDAY.value - today.dayOfWeek.value + 7) % 7
        val nextFriday = if (daysUntilFriday == 0 && today.dayOfWeek == DayOfWeek.FRIDAY) {
            today
        } else {
            today.plusDays(daysUntilFriday.toLong())
        }
        
        // Calculate sunset time for Friday using actual location
        val zoneId = ZoneId.systemDefault()
        val fridaySunset = SunsetCalculator.calculateSunsetTime(nextFriday, latitude, longitude, zoneId)
            ?: nextFriday.atTime(18, 0).atZone(zoneId)
        
        // Check if we're 1 hour before sunset
        val oneHourBefore = fridaySunset.minusHours(1)
        val oneHourAfter = fridaySunset.plusHours(1)
        
        if (now.isAfter(oneHourBefore) && now.isBefore(oneHourAfter)) {
            // Check if we've already shown this reminder today
            val lastShabbatReminder = settings.getLastShabbatReminderEpochDay()
            if (lastShabbatReminder != nextFriday.toEpochDay()) {
                Notifier.showShabbatReminder(applicationContext, nextFriday)
                settings.setLastShabbatReminderEpochDay(nextFriday.toEpochDay())
            }
        }
    }
    
    private suspend fun checkFeastReminders(repo: LunarRepository, settings: SettingsRepository) {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        
        // Get feasts for current and next year
        val currentYear = repo.getToday()?.yearNumber ?: return
        val feasts = repo.feastDaysForYear(currentYear)
        val nextYearFeasts = repo.feastDaysForYear(currentYear + 1)
        
        // Check if tomorrow is a feast day
        val tomorrowFeasts = (feasts + nextYearFeasts).filter { it.date == tomorrow }
        
        if (tomorrowFeasts.isNotEmpty()) {
            // Check if we've already shown reminder for this feast
            val lastFeastReminder = settings.getLastFeastReminderEpochDay()
            if (lastFeastReminder != tomorrow.toEpochDay()) {
                // Show reminder for the first feast (or combine them)
                val feastTitle = tomorrowFeasts.first().title.replace(Regex("\\([^)]+\\)"), "").trim()
                Notifier.showFeastReminder(applicationContext, feastTitle, tomorrow)
                settings.setLastFeastReminderEpochDay(tomorrow.toEpochDay())
            }
        }
    }

    private suspend fun maybePromptMoon(repo: LunarRepository, settings: SettingsRepository, today: com.example.biblicalmonth.domain.LunarDate) {
        val epochDay = LocalDate.now().toEpochDay()
        val alreadyPrompted = settings.getLastMoonPromptEpochDay() == epochDay
        if (alreadyPrompted) return

        val shouldPrompt = (today.dayOfMonth == 29 || today.dayOfMonth == 30)
        if (!shouldPrompt) return

        val tomorrow = LocalDate.now().plusDays(1)
        if (repo.hasMonthStartOn(tomorrow)) return // already confirmed

        Notifier.showMoonPrompt(applicationContext, today.dayOfMonth)
        settings.setLastMoonPromptEpochDay(epochDay)
    }

    private suspend fun maybePromptBarley(repo: LunarRepository, settings: SettingsRepository, today: com.example.biblicalmonth.domain.LunarDate) {
        if (today.monthNumber != 12 || today.dayOfMonth != 29) return

        val epochDay = LocalDate.now().toEpochDay()
        val alreadyPrompted = settings.getLastAvivPromptEpochDay() == epochDay
        if (alreadyPrompted) return

        val decision = repo.getBarleyDecision(today.yearNumber)
        if (decision != null) return

        Notifier.showBarleyPrompt(applicationContext, today.yearNumber)
        settings.setLastAvivPromptEpochDay(epochDay)
    }
}

