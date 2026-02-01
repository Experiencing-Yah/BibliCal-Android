package com.experiencingyah.bibliCal.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import com.experiencingyah.bibliCal.notifications.Notifier
import com.experiencingyah.bibliCal.util.SunsetCalculator
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyTickWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repo = LunarRepository(applicationContext)
        val settings = SettingsRepository(applicationContext)

        // Use shared logic for status notification and widget updates
        val today = StatusUpdater.updateStatusAndWidgets(applicationContext, repo, settings)
        
        if (today != null) {
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
        
        // Use cached location (or defaults) - no fresh location fetch in background
        val (latitude, longitude) = StatusUpdater.getLocation(settings)
        
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

    private suspend fun maybePromptMoon(repo: LunarRepository, settings: SettingsRepository, today: com.experiencingyah.bibliCal.domain.LunarDate) {
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

    private suspend fun maybePromptBarley(repo: LunarRepository, settings: SettingsRepository, today: com.experiencingyah.bibliCal.domain.LunarDate) {
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

