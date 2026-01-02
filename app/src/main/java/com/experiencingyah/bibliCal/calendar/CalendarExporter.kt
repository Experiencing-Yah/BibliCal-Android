package com.experiencingyah.bibliCal.calendar

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.time.ZoneId

class CalendarExporter(private val context: Context) {
    fun listCalendars(): List<DeviceCalendar> {
        val resolver = context.contentResolver
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
        )
        val calendars = mutableListOf<DeviceCalendar>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val acctIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            while (cursor.moveToNext()) {
                calendars.add(
                    DeviceCalendar(
                        id = cursor.getLong(idIdx),
                        displayName = cursor.getString(nameIdx) ?: "Calendar",
                        accountName = cursor.getString(acctIdx) ?: "",
                    )
                )
            }
        }
        return calendars
    }

    fun exportAllDayEvents(calendarId: Long, events: List<AllDayCalendarEvent>): Int {
        val resolver = context.contentResolver
        val tz = ZoneId.systemDefault().id
        var created = 0
        events.forEach { e ->
            val startMillis = e.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis = e.date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, e.title)
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, tz)
                e.description?.let { put(CalendarContract.Events.DESCRIPTION, it) }
            }
            val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) created++
        }
        return created
    }
}

data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
)

data class AllDayCalendarEvent(
    val title: String,
    val date: java.time.LocalDate,
    val description: String? = null,
)

