package com.example.biblicalmonth.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.biblicalmonth.data.LunarRepository
import com.example.biblicalmonth.data.settings.SettingsRepository
import com.example.biblicalmonth.notifications.Notifier
import com.example.biblicalmonth.util.MonthNames
import com.example.biblicalmonth.widgets.TodayWidgetProvider
import kotlinx.coroutines.flow.first
import java.time.LocalDate

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
        }

        return Result.success()
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

