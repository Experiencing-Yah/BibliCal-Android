package com.experiencingyah.bibliCal.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.util.Log
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
import androidx.compose.material3.HorizontalDivider
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
import com.experiencingyah.bibliCal.calendar.AllDayCalendarEvent
import com.experiencingyah.bibliCal.calendar.CalendarExporter
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import com.experiencingyah.bibliCal.integrations.PassagesIntegration
import com.experiencingyah.bibliCal.ui.vm.SettingsViewModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()
    val settingsRepo = remember { SettingsRepository(context) }

    var firstfruitsMenuOpen by remember { mutableStateOf(false) }
    var calendarMenuOpen by remember { mutableStateOf(false) }
    var calendars by remember { mutableStateOf(emptyList<com.experiencingyah.bibliCal.calendar.DeviceCalendar>()) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var passagesSyncStatus by remember { mutableStateOf<String?>(null) }
    
    // Track calendar permission state
    var hasCalendarPermission by remember { mutableStateOf(false) }
    
    // Check calendar permission on launch
    LaunchedEffect(Unit) {
        val canRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val canWrite = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        hasCalendarPermission = canRead && canWrite
    }

    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // no-op; UI reads state from toggles
    }

    val calendarPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        // Update permission state and refresh list
        val canRead = permissions[Manifest.permission.READ_CALENDAR] == true || 
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val canWrite = permissions[Manifest.permission.WRITE_CALENDAR] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        hasCalendarPermission = canRead && canWrite
        if (hasCalendarPermission) {
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

                HorizontalDivider()

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

                HorizontalDivider()

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

                HorizontalDivider()

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

                HorizontalDivider()

                // PassAges Sync Section
                Text("PassAges", style = MaterialTheme.typography.titleLarge)
                Text("Send the current week to PassAges.", style = MaterialTheme.typography.bodySmall)
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        passagesSyncStatus = null
                        scope.launch {
                            val week = PassagesIntegration.getCurrentParshaWeekIndex(context)
                            if (week == null) {
                                passagesSyncStatus = "Unable to determine the current week."
                                return@launch
                            }
                            val callbackUri = Uri.parse("passages://set-week?week=$week")
                            val intent = Intent(Intent.ACTION_VIEW, callbackUri)
                                .addCategory(Intent.CATEGORY_BROWSABLE)
                            val pm = context.packageManager
                            val resolved = intent.resolveActivity(pm)
                            val matches = pm.queryIntentActivities(
                                intent,
                                PackageManager.MATCH_DEFAULT_ONLY
                            )
                            Log.d(
                                "PassAges",
                                "Sync intent: uri=$callbackUri resolved=$resolved matches=${matches.size}"
                            )
                            val canOpen = resolved != null || matches.isNotEmpty()
                            if (canOpen) {
                                context.startActivity(intent)
                                passagesSyncStatus = "Sent week $week to PassAges."
                            } else {
                                passagesSyncStatus = "PassAges is not installed."
                            }
                        }
                    }
                ) { Text("Sync with PassAges", color = androidx.compose.ui.graphics.Color.White) }
                passagesSyncStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                HorizontalDivider()

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

                HorizontalDivider()

                // Calendar Export Section
                Text("Calendar Export", style = MaterialTheme.typography.titleLarge)
                Text("Exports feast days into a selected device calendar.", style = MaterialTheme.typography.bodySmall)

                if (!hasCalendarPermission) {
                    // Show permission request UI
                    Text(
                        "Calendar permission is required to export feast days to your device calendar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = {
                        val perms = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                        calendarPermLauncher.launch(perms)
                    }) { Text("Grant Calendar Permission", color = androidx.compose.ui.graphics.Color.White) }
                } else {
                    // Permission granted - show calendar selection and export options
                    Button(onClick = {
                        scope.launch { calendars = CalendarExporter(context).listCalendars() }
                        calendarMenuOpen = true
                    }) { 
                        Text(
                            if (state.selectedCalendarId == -1L) "Pick a calendar" 
                            else "Calendar ID: ${state.selectedCalendarId}", 
                            color = androidx.compose.ui.graphics.Color.White
                        ) 
                    }

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
                    
                    if (state.selectedCalendarId == -1L) {
                        Text(
                            "Select a calendar above to enable export.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (!state.hasAnchor) {
                        Text(
                            "Set a date from the Today tab to enable export.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
            val cached = settingsRepo.getCachedLocation()
            val cachedLocation = cached?.let {
                Location("cached").apply {
                    latitude = it.first
                    longitude = it.second
                }
            }
            try {
                val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
                val cancellationTokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                val locationTask = fusedLocationClient.getCurrentLocation(
                    com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationTokenSource.token
                )
                val location = Tasks.await(locationTask, 5, java.util.concurrent.TimeUnit.SECONDS)
                val locationToUse = location ?: cachedLocation
                
                if (locationToUse != null) {
                    val todaySunset = com.experiencingyah.bibliCal.util.SunsetCalculator.calculateSunsetTime(
                        todayDate, locationToUse.latitude, locationToUse.longitude, zoneId
                    )
                    val now = java.time.ZonedDateTime.now(zoneId)
                    val isAfterSunset = todaySunset != null && now.isAfter(todaySunset)
                    val daytimeDate = if (isAfterSunset) {
                        todayDate.plusDays(1).toString()
                    } else {
                        todayDate.toString()
                    }
                    val sunsetDate = if (isAfterSunset) {
                        todayDate.toString()
                    } else {
                        todayDate.minusDays(1).toString()
                    }
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
        
        com.experiencingyah.bibliCal.ui.screens.DatePickerDialog(
            onDismiss = { showDatePicker = false },
            onConfirm = { y, m, d ->
                scope.launch {
                    val repo = LunarRepository(context)
                    // Use the biblical reference date (tomorrow if after sunset)
                    val referenceDate = daytimeDate?.let { java.time.LocalDate.parse(it) }
                        ?: java.time.LocalDate.now()
                    
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

