package com.experiencingyah.bibliCal.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import com.experiencingyah.bibliCal.widgets.DateWidgetProvider
import com.experiencingyah.bibliCal.widgets.ShabbatWidgetProvider
import com.experiencingyah.bibliCal.widgets.CombinedWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val repo = LunarRepository(context)
        val settings = SettingsRepository(context)

        when (intent.action) {
            ACTION_MOON_SEEN -> {
                repo.startNextMonthOn(LocalDate.now().plusDays(1))
            }
            ACTION_MOON_NOT_SEEN -> {
                // If it's already day 30, the next month must start tomorrow.
                val today = repo.getToday()
                if (today?.dayOfMonth == 30) {
                    repo.startNextMonthOn(LocalDate.now().plusDays(1))
                }
            }
            ACTION_MOON_LATER -> Unit

            ACTION_BARLEY_AVIV -> {
                val year = intent.getIntExtra(EXTRA_YEAR_NUMBER, -1)
                if (year != -1) repo.setBarleyAvivDecision(year, aviv = true, decidedOn = LocalDate.now())
            }
            ACTION_BARLEY_NOT_AVIV -> {
                val year = intent.getIntExtra(EXTRA_YEAR_NUMBER, -1)
                if (year != -1) repo.setBarleyAvivDecision(year, aviv = false, decidedOn = LocalDate.now())
            }
            ACTION_BARLEY_LATER -> Unit
        }

        // Refresh surfaces quickly.
        val today = repo.getToday()
        if (today != null) {
            val namingMode = settings.monthNamingMode.first()
            val label = com.experiencingyah.bibliCal.util.MonthNames.format(today.monthNumber, namingMode) +
                " ${today.dayOfMonth}, Year ${today.yearNumber}"
            Notifier.ensureChannels(context)
            if (settings.statusNotificationEnabled.first()) {
                Notifier.showOrUpdateStatus(context, today, label)
            } else {
                Notifier.cancelStatus(context)
            }
            DateWidgetProvider.updateAll(context)
            ShabbatWidgetProvider.updateAll(context)
            CombinedWidgetProvider.updateAll(context)
        }
    }

    companion object {
        const val EXTRA_YEAR_NUMBER = "extra_year_number"

        const val ACTION_MOON_SEEN = "com.experiencingyah.bibliCal.action.MOON_SEEN"
        const val ACTION_MOON_NOT_SEEN = "com.experiencingyah.bibliCal.action.MOON_NOT_SEEN"
        const val ACTION_MOON_LATER = "com.experiencingyah.bibliCal.action.MOON_LATER"

        const val ACTION_BARLEY_AVIV = "com.experiencingyah.bibliCal.action.BARLEY_AVIV"
        const val ACTION_BARLEY_NOT_AVIV = "com.experiencingyah.bibliCal.action.BARLEY_NOT_AVIV"
        const val ACTION_BARLEY_LATER = "com.experiencingyah.bibliCal.action.BARLEY_LATER"
    }
}

