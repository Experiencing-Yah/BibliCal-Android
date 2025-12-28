package com.example.biblicalmonth.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
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
import com.google.android.gms.tasks.Tasks
import com.example.biblicalmonth.calendar.AllDayCalendarEvent
import com.example.biblicalmonth.calendar.CalendarExporter
import com.example.biblicalmonth.data.LunarRepository
import com.example.biblicalmonth.data.settings.MonthNamingMode
import com.example.biblicalmonth.ui.vm.SettingsViewModel
import java.time.LocalDate
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

    var showDatePicker by remember { mutableStateOf(false) }
    var selectedYear by remember { mutableStateOf(2024) }
    var selectedMonth by remember { mutableStateOf(1) }
    var selectedDay by remember { mutableStateOf(1) }
    var selectedGregorianDate by remember { mutableStateOf(java.time.LocalDate.now()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Notifications Section
                Text("Notifications", style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Ongoing notification", style = MaterialTheme.typography.titleMedium)
                        Text("Show today's lunar date in a persistent notification.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.statusNotificationEnabled, onCheckedChange = { vm.setStatusNotificationEnabled(it) })
                }

                Button(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text("Grant notification permission", color = androidx.compose.ui.graphics.Color.White)
                }

                Divider()

                // Prompts Section
                Text("Prompts", style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Enable prompts", style = MaterialTheme.typography.titleMedium)
                        Text("Ask on day 29/30 about the new moon, and on 12/29 about aviv barley.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.promptsEnabled, onCheckedChange = { vm.setPromptsEnabled(it) })
                }

                Divider()

                // Month Naming Section
                Text("Month Naming", style = MaterialTheme.typography.titleLarge)
                Button(onClick = { monthModeMenuOpen = true }) {
                    val label = when (state.monthNamingMode) {
                        MonthNamingMode.ORDINAL -> "Ordinal (First, Second, …)"
                        MonthNamingMode.NUMBERED -> "Numbered (Month 1, Month 2, …)"
                    }
                    Text(label, color = androidx.compose.ui.graphics.Color.White)
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

                Divider()

                // Optional Holidays Section
                Text("Optional Holidays", style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Include Hanukkah", style = MaterialTheme.typography.titleMedium)
                        Text("Show Hanukkah (8 days starting 25th of 9th month) in calendar.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.includeHanukkah, onCheckedChange = { vm.setIncludeHanukkah(it) })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Include Purim", style = MaterialTheme.typography.titleMedium)
                        Text("Show Purim (14th of 12th month) in calendar.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.includePurim, onCheckedChange = { vm.setIncludePurim(it) })
                }

                Divider()

                // Jerusalem Time Section
                Text("Jerusalem Time", style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Show Jerusalem Time", style = MaterialTheme.typography.titleMedium)
                        Text("Display current time, sunset time, and countdown for Jerusalem on the Today screen.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.showJerusalemTime, onCheckedChange = { vm.setShowJerusalemTime(it) })
                }

                Divider()

                // Date Adjustment Section
                Text("Date Adjustment", style = MaterialTheme.typography.titleLarge)
                Button(
                    enabled = state.hasAnchor,
                    onClick = { 
                        scope.launch {
                            val repo = LunarRepository(context)
                            val today = repo.getToday()
                            if (today != null) {
                                selectedYear = today.yearNumber
                                selectedMonth = today.monthNumber
                                selectedDay = today.dayOfMonth
                                selectedGregorianDate = java.time.LocalDate.now()
                            }
                            showDatePicker = true
                        }
                    }
                ) {
                    Text("Set Current Date", color = androidx.compose.ui.graphics.Color.White)
                }
                if (!state.hasAnchor) {
                    Text(
                        "Set a date first from the Today tab.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Divider()

                // Calendar Export Section
                Text("Calendar Export", style = MaterialTheme.typography.titleLarge)
                Text("Exports feast days into a selected device calendar.", style = MaterialTheme.typography.bodySmall)

                Button(onClick = {
                    val perms = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                    calendarPermLauncher.launch(perms)
                }) { Text("Grant calendar permission", color = androidx.compose.ui.graphics.Color.White) }

                Button(onClick = {
                    val canRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                    val canWrite = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
                    if (!canRead || !canWrite) {
                        exportStatus = "Grant calendar permission first."
                        return@Button
                    }
                    scope.launch { calendars = CalendarExporter(context).listCalendars() }
                    calendarMenuOpen = true
                }) { Text(if (state.selectedCalendarId == -1L) "Pick a calendar" else "Calendar ID: ${state.selectedCalendarId}", color = androidx.compose.ui.graphics.Color.White) }

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

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
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
                    ) { Text("Export current year feasts", color = androidx.compose.ui.graphics.Color.White) }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
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
                    ) { Text("Export next year feasts", color = androidx.compose.ui.graphics.Color.White) }
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

    var datePickerState by remember { 
        mutableStateOf<Triple<Int, Int, Int>?>(null) 
    }
    var datePickerSunsetInfo by remember { 
        mutableStateOf<Triple<Boolean, String?, String?>?>(null) 
    }
    
    LaunchedEffect(showDatePicker) {
        if (showDatePicker) {
            val repo = LunarRepository(context)
            val today = repo.getToday()
            val year = today?.yearNumber ?: selectedYear
            val month = today?.monthNumber ?: selectedMonth
            val day = today?.dayOfMonth ?: selectedDay
            
            datePickerState = Triple(year, month, day)
            
            // Get sunset info
            val todayDate = java.time.LocalDate.now()
            val zoneId = java.time.ZoneId.systemDefault()
            try {
                val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
                val cancellationTokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                val locationTask = fusedLocationClient.getCurrentLocation(
                    com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationTokenSource.token
                )
                val location = Tasks.await(locationTask, 5, java.util.concurrent.TimeUnit.SECONDS)
                
                if (location != null) {
                    val todaySunset = com.example.biblicalmonth.util.SunsetCalculator.calculateSunsetTime(
                        todayDate, location.latitude, location.longitude, zoneId
                    )
                    val now = java.time.ZonedDateTime.now(zoneId)
                    val isAfterSunset = todaySunset != null && now.isAfter(todaySunset)
                    val daytimeDate = todayDate.toString()
                    val sunsetDate = if (isAfterSunset) todayDate.toString() else todayDate.minusDays(1).toString()
                    datePickerSunsetInfo = Triple(isAfterSunset, daytimeDate, sunsetDate)
                } else {
                    datePickerSunsetInfo = Triple(false, todayDate.toString(), todayDate.minusDays(1).toString())
                }
            } catch (e: Exception) {
                datePickerSunsetInfo = Triple(false, todayDate.toString(), todayDate.minusDays(1).toString())
            }
        }
    }
    
    if (showDatePicker && datePickerState != null) {
        val (year, month, day) = datePickerState!!
        val (isAfterSunset, daytimeDate, sunsetDate) = datePickerSunsetInfo ?: Triple(false, null, null)
        
        com.example.biblicalmonth.ui.screens.DatePickerDialog(
            onDismiss = { showDatePicker = false },
            onConfirm = { y, m, d ->
                scope.launch {
                    val repo = LunarRepository(context)
                    // Automatically use the appropriate date based on context (after sunset or not)
                    val referenceDate = if (isAfterSunset) {
                        // After sunset: use sunset date (today's date, since biblical day started at sunset)
                        sunsetDate?.let { java.time.LocalDate.parse(it) } ?: java.time.LocalDate.now()
                    } else {
                        // Before sunset: use daytime date (today's date)
                        daytimeDate?.let { java.time.LocalDate.parse(it) } ?: java.time.LocalDate.now()
                    }
                    
                    // Calculate month start: if day X occurs on referenceDate, month started (day-1) days earlier
                    val monthStart = referenceDate.minusDays((d - 1).toLong())
                    repo.setAnchor(y, m, monthStart)
                    vm.refresh() // Refresh to update hasAnchor state
                }
                showDatePicker = false
            },
            initialYear = year,
            initialMonth = month,
            initialDay = day,
            isAfterSunset = isAfterSunset,
            daytimeDate = daytimeDate,
            sunsetDate = sunsetDate
        )
    }
}

