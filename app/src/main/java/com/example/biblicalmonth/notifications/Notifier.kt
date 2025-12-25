package com.example.biblicalmonth.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.biblicalmonth.R
import com.example.biblicalmonth.domain.LunarDate
import com.example.biblicalmonth.ui.MainActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Notifier {
    const val CHANNEL_STATUS = "status"
    const val CHANNEL_PROMPTS = "prompts"

    const val NOTIF_ID_STATUS = 1001
    const val NOTIF_ID_PROMPT_MOON = 2001
    const val NOTIF_ID_PROMPT_BARLEY = 2002

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.notification_channel_status_name),
                NotificationManager.IMPORTANCE_LOW
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

        val gregorian = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        val notif = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification_moon)
            .setContentTitle("Today: $lunarLabel")
            .setContentText("Gregorian: $gregorian")
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
            .setSmallIcon(R.drawable.ic_notification_barley)
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

