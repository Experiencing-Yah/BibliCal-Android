package com.example.biblicalmonth.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.biblicalmonth.calendar.AllDayCalendarEvent
import com.example.biblicalmonth.calendar.CalendarExporter
import com.example.biblicalmonth.data.LunarRepository
import com.example.biblicalmonth.data.settings.FirstfruitsRule
import com.example.biblicalmonth.data.settings.MonthNamingMode
import com.example.biblicalmonth.ui.vm.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()

    var monthModeMenuOpen by remember { mutableStateOf(false) }
    var firstfruitsMenuOpen by remember { mutableStateOf(false) }
    var calendarMenuOpen by remember { mutableStateOf(false) }
    var calendars by remember { mutableStateOf(emptyList<com.example.biblicalmonth.calendar.DeviceCalendar>()) }
    var exportStatus by remember { mutableStateOf<String?>(null) }

    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // no-op; UI reads state from toggles
    }

    val calendarPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        // refresh list
        val canRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val canWrite = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (canRead && canWrite) {
            scope.launch { calendars = CalendarExporter(context).listCalendars() }
        }
    }

    LaunchedEffect(Unit) {
        vm.refresh()
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Ongoing notification", style = MaterialTheme.typography.titleMedium)
                        Text("Show today’s lunar date in a persistent notification.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.statusNotificationEnabled, onCheckedChange = { vm.setStatusNotificationEnabled(it) })
                }

                Button(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text("Grant notification permission")
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Prompts", style = MaterialTheme.typography.titleMedium)
                        Text("Ask on day 29/30 about the new moon, and on 12/29 about aviv barley.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.promptsEnabled, onCheckedChange = { vm.setPromptsEnabled(it) })
                }
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Month naming", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { monthModeMenuOpen = true }) {
                    Text("Mode: ${state.monthNamingMode.name.lowercase()}")
                }
                DropdownMenu(expanded = monthModeMenuOpen, onDismissRequest = { monthModeMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Ordinal (First, Second, …)") },
                        onClick = {
                            vm.setMonthNamingMode(MonthNamingMode.ORDINAL)
                            monthModeMenuOpen = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Numbered (Month 1, Month 2, …)") },
                        onClick = {
                            vm.setMonthNamingMode(MonthNamingMode.NUMBERED)
                            monthModeMenuOpen = false
                        }
                    )
                }
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Feast projection rules", style = MaterialTheme.typography.titleMedium)
                Text("Controls how Firstfruits/Shavuot are projected.", style = MaterialTheme.typography.bodySmall)

                Button(onClick = { firstfruitsMenuOpen = true }) {
                    val label = when (state.firstfruitsRule) {
                        FirstfruitsRule.FIXED_DAY_16 -> "Fixed (16/1)"
                        FirstfruitsRule.SUNDAY_DURING_UNLEAVENED_BREAD -> "Sunday during UB (16–22)"
                    }
                    Text("Firstfruits: $label")
                }

                DropdownMenu(expanded = firstfruitsMenuOpen, onDismissRequest = { firstfruitsMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Fixed: 16th day of Month 1") },
                        onClick = {
                            vm.setFirstfruitsRule(FirstfruitsRule.FIXED_DAY_16)
                            firstfruitsMenuOpen = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("First Sunday during Unleavened Bread (days 16–22)") },
                        onClick = {
                            vm.setFirstfruitsRule(FirstfruitsRule.SUNDAY_DURING_UNLEAVENED_BREAD)
                            firstfruitsMenuOpen = false
                        }
                    )
                }
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Calendar export", style = MaterialTheme.typography.titleMedium)
                Text("Exports feast days into a selected device calendar.", style = MaterialTheme.typography.bodySmall)

                Button(onClick = {
                    val perms = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                    calendarPermLauncher.launch(perms)
                }) { Text("Grant calendar permission") }

                Button(onClick = {
                    val canRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                    val canWrite = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
                    if (!canRead || !canWrite) {
                        exportStatus = "Grant calendar permission first."
                        return@Button
                    }
                    scope.launch { calendars = CalendarExporter(context).listCalendars() }
                    calendarMenuOpen = true
                }) { Text(if (state.selectedCalendarId == -1L) "Pick a calendar" else "Calendar ID: ${state.selectedCalendarId}") }

                DropdownMenu(expanded = calendarMenuOpen, onDismissRequest = { calendarMenuOpen = false }) {
                    calendars.forEach { cal ->
                        DropdownMenuItem(
                            text = { Text("${cal.displayName} (${cal.accountName})") },
                            onClick = {
                                vm.setSelectedCalendarId(cal.id)
                                calendarMenuOpen = false
                            }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = state.selectedCalendarId != -1L && state.hasAnchor,
                        onClick = {
                            exportStatus = null
                            scope.launch {
                                val canRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                                val canWrite = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
                                if (!canRead || !canWrite) {
                                    exportStatus = "Grant calendar permission first."
                                    return@launch
                                }
                                val repo = LunarRepository(context)
                                val year = repo.getToday()?.yearNumber
                                if (year == null) {
                                    exportStatus = "No year available; set an anchor first."
                                    return@launch
                                }
                                val feasts = repo.feastDaysForYear(year)
                                val months = repo.monthsForYear(year)
                                val events = buildList {
                                    addAll(months.map { AllDayCalendarEvent("Month ${it.monthNumber} begins", it.startDate, "Biblical Month") })
                                    addAll(feasts.map { AllDayCalendarEvent(it.title, it.date, "Biblical Month (Year $year)") })
                                }
                                val created = withContext(Dispatchers.IO) {
                                    CalendarExporter(context).exportAllDayEvents(state.selectedCalendarId, events)
                                }
                                exportStatus = "Exported $created events for Year $year."
                            }
                        }
                    ) { Text("Export current year feasts") }

                    Button(
                        enabled = state.selectedCalendarId != -1L && state.hasAnchor,
                        onClick = {
                            exportStatus = null
                            scope.launch {
                                val canRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                                val canWrite = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
                                if (!canRead || !canWrite) {
                                    exportStatus = "Grant calendar permission first."
                                    return@launch
                                }
                                val repo = LunarRepository(context)
                                val year = repo.getToday()?.yearNumber?.plus(1)
                                if (year == null) {
                                    exportStatus = "No year available; set an anchor first."
                                    return@launch
                                }
                                val feasts = repo.feastDaysForYear(year)
                                val months = repo.monthsForYear(year)
                                val events = buildList {
                                    addAll(months.map { AllDayCalendarEvent("Month ${it.monthNumber} begins", it.startDate, "Biblical Month") })
                                    addAll(feasts.map { AllDayCalendarEvent(it.title, it.date, "Biblical Month (Year $year)") })
                                }
                                val created = withContext(Dispatchers.IO) {
                                    CalendarExporter(context).exportAllDayEvents(state.selectedCalendarId, events)
                                }
                                exportStatus = "Exported $created events for Year $year."
                            }
                        }
                    ) { Text("Export next year feasts") }
                }

                exportStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "Tip: Month starts are confirmed via prompts on day 29/30, or manually from the Today tab.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

