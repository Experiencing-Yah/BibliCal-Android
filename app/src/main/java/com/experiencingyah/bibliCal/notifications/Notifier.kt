package com.experiencingyah.bibliCal.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.experiencingyah.bibliCal.R
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import com.experiencingyah.bibliCal.domain.LunarDate
import com.experiencingyah.bibliCal.ui.MainActivity
import com.experiencingyah.bibliCal.util.MonthNames
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Notifier {
    const val CHANNEL_STATUS = "status"
    const val CHANNEL_PROMPTS = "prompts"

    const val NOTIF_ID_STATUS = 1001
    const val NOTIF_ID_PROMPT_MOON = 2001
    const val NOTIF_ID_PROMPT_BARLEY = 2002
    const val NOTIF_ID_SHABBAT_REMINDER = 3001
    const val NOTIF_ID_FEAST_REMINDER = 3002

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.notification_channel_status_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROMPTS,
                context.getString(R.string.notification_channel_prompts_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    fun showOrUpdateStatus(context: Context, lunarDate: LunarDate, lunarLabel: String) {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Format: "4th Day of the Tenth Month" on top line, "Year 6025" on second line
        val dayOrdinal = when (lunarDate.dayOfMonth) {
            1 -> "1st"
            2 -> "2nd"
            3 -> "3rd"
            21 -> "21st"
            22 -> "22nd"
            23 -> "23rd"
            31 -> "31st"
            else -> "${lunarDate.dayOfMonth}th"
        }
        
        // Get month name using the same naming mode as the app
        val settings = SettingsRepository(context)
        val namingMode = runBlocking { settings.monthNamingMode.first() }
        val monthName = MonthNames.format(lunarDate.monthNumber, namingMode)
        
        val title = "$dayOrdinal Day of the $monthName Month"
        val yearText = "Year ${lunarDate.yearNumber}"

        val notif = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification_moon)
            .setContentTitle(title)
            .setContentText(yearText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_STATUS, notif)
    }

    fun cancelStatus(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_STATUS)
    }

    fun showMoonPrompt(context: Context, dayOfMonth: Int) {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val seenPi = actionPI(context, NotificationActionReceiver.ACTION_MOON_SEEN, 10)
        val notSeenPi = actionPI(context, NotificationActionReceiver.ACTION_MOON_NOT_SEEN, 11)
        val laterPi = actionPI(context, NotificationActionReceiver.ACTION_MOON_LATER, 12)

        val notif = NotificationCompat.Builder(context, CHANNEL_PROMPTS)
            .setSmallIcon(R.drawable.ic_notification_moon)
            .setContentTitle("New moon check (day $dayOfMonth)")
            .setContentText("Was the new moon seen?")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(0, "Seen", seenPi)
            .addAction(0, "Not seen", notSeenPi)
            .addAction(0, "Later", laterPi)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_PROMPT_MOON, notif)
    }

    fun showBarleyPrompt(context: Context, yearNumber: Int) {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val avivPi = actionPI(context, NotificationActionReceiver.ACTION_BARLEY_AVIV, 20, yearNumber)
        val notPi = actionPI(context, NotificationActionReceiver.ACTION_BARLEY_NOT_AVIV, 21, yearNumber)
        val laterPi = actionPI(context, NotificationActionReceiver.ACTION_BARLEY_LATER, 22, yearNumber)

        val notif = NotificationCompat.Builder(context, CHANNEL_PROMPTS)
            .setSmallIcon(R.drawable.ic_notification_moon)
            .setContentTitle("Aviv barley check (year $yearNumber)")
            .setContentText("Is the barley aviv? (Decides new year vs 13th month)")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(0, "Aviv", avivPi)
            .addAction(0, "Not aviv", notPi)
            .addAction(0, "Later", laterPi)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_PROMPT_BARLEY, notif)
    }

    fun showShabbatReminder(context: Context, fridayDate: java.time.LocalDate) {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_PROMPTS)
            .setSmallIcon(R.drawable.ic_notification_moon)
            .setContentTitle("Shabbat begins in 1 hour")
            .setContentText("Shabbat begins at sunset on ${fridayDate.format(DateTimeFormatter.ofPattern("MMM d"))}")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_SHABBAT_REMINDER, notif)
    }

    fun showFeastReminder(context: Context, feastTitle: String, feastDate: java.time.LocalDate) {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_PROMPTS)
            .setSmallIcon(R.drawable.ic_notification_moon)
            .setContentTitle("$feastTitle tomorrow")
            .setContentText("$feastTitle begins at sunset on ${feastDate.format(DateTimeFormatter.ofPattern("MMM d"))}")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_FEAST_REMINDER + feastDate.hashCode(), notif)
    }

    private fun actionPI(context: Context, action: String, requestCode: Int, yearNumber: Int? = null): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            yearNumber?.let { putExtra(NotificationActionReceiver.EXTRA_YEAR_NUMBER, it) }
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

