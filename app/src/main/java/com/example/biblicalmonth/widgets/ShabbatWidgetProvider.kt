package com.example.biblicalmonth.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.biblicalmonth.R
import com.example.biblicalmonth.ui.MainActivity

class ShabbatWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context, appWidgetManager, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // When widget is first added, ensure periodic updates are scheduled
        com.example.biblicalmonth.work.AppSchedulers.ensureScheduled(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(android.content.ComponentName(context, ShabbatWidgetProvider::class.java))
            updateAll(context, mgr, ids)
        }

        private fun updateAll(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return

            val data = WidgetHelper.getWidgetData(context)
            val clickPi = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_shabbat_only).apply {
                    setTextViewText(R.id.widget_shabbat_countdown, data.shabbatText)
                    setTextViewText(R.id.widget_shabbat_label, data.shabbatLabel)
                    // Set gold color for Shabbat text
                    if (data.isShabbat) {
                        setTextColor(R.id.widget_shabbat_countdown, 0xFFFFD700.toInt())
                    } else {
                        setTextColor(R.id.widget_shabbat_countdown, 0xFFFFFFFF.toInt())
                    }
                    setOnClickPendingIntent(R.id.widget_shabbat_countdown, clickPi)
                    setOnClickPendingIntent(R.id.widget_shabbat_label, clickPi)
                }
                mgr.updateAppWidget(id, views)
            }
        }
    }
}

