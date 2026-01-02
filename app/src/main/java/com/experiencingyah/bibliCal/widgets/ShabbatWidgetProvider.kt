package com.experiencingyah.bibliCal.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import com.experiencingyah.bibliCal.R
import com.experiencingyah.bibliCal.ui.MainActivity

class ShabbatWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        // Handle widget resize - update the widget when size changes
        updateAll(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // When widget is first added, ensure periodic updates are scheduled
        com.experiencingyah.bibliCal.work.AppSchedulers.ensureScheduled(context)
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
                // Get widget dimensions to calculate text size
                val options = mgr.getAppWidgetOptions(id)
                val widgetHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                
                // Calculate text sizes based on widget height
                // Base size is for 1 cell (~70dp), scale proportionally
                val baseHeight = 70f // 1 cell in dp
                // Use widget height if available, otherwise default to base height
                val actualHeight = if (widgetHeight > 0) widgetHeight.toFloat() else baseHeight
                val scaleFactor = (actualHeight / baseHeight).coerceIn(0.8f, 2.5f) // Limit scaling between 0.8x and 2.5x
                
                // Base text sizes (for 1 cell height)
                val baseCountdownSize = 18f
                val baseLabelSize = 12f
                
                // Calculate scaled text sizes
                val countdownSize = baseCountdownSize * scaleFactor
                val labelSize = baseLabelSize * scaleFactor
                
                val views = RemoteViews(context.packageName, R.layout.widget_shabbat_only).apply {
                    setTextViewText(R.id.widget_shabbat_countdown, data.shabbatText)
                    setTextViewText(R.id.widget_shabbat_label, data.shabbatLabel)
                    
                    // Set dynamic text sizes (in scaled pixels)
                    setTextViewTextSize(R.id.widget_shabbat_countdown, TypedValue.COMPLEX_UNIT_SP, countdownSize)
                    setTextViewTextSize(R.id.widget_shabbat_label, TypedValue.COMPLEX_UNIT_SP, labelSize)
                    
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

