package com.example.biblicalmonth.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.biblicalmonth.R
import com.example.biblicalmonth.data.LunarRepository
import com.example.biblicalmonth.data.settings.SettingsRepository
import com.example.biblicalmonth.ui.MainActivity
import com.example.biblicalmonth.util.MonthNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(android.content.ComponentName(context, TodayWidgetProvider::class.java))
            updateAll(context, mgr, ids)
        }

        private fun updateAll(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return

            val (lunarText, gregorianText) = runBlocking {
                withContext(Dispatchers.IO) {
                    val repo = LunarRepository(context)
                    val today = repo.getToday()
                    if (today == null) {
                        "Tap to set an anchor" to LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    } else {
                        val namingMode = SettingsRepository(context).monthNamingMode.first()
                        val monthName = MonthNames.format(today.monthNumber, namingMode)
                        val label = "$monthName ${today.dayOfMonth} (Y${today.yearNumber})"
                        label to LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    }
                }
            }

            val clickPi = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.today_widget).apply {
                    setTextViewText(R.id.widget_lunar_date, lunarText)
                    setTextViewText(R.id.widget_gregorian_date, gregorianText)
                    setOnClickPendingIntent(R.id.widget_lunar_date, clickPi)
                    setOnClickPendingIntent(R.id.widget_gregorian_date, clickPi)
                }
                mgr.updateAppWidget(id, views)
            }
        }
    }
}

