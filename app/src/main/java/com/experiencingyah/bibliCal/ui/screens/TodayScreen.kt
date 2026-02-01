package com.experiencingyah.bibliCal.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
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
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.ui.vm.TodayViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(
    vm: TodayViewModel = viewModel(),
    onNavigateToWidgetShowcase: () -> Unit = {}
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedYear by remember { mutableStateOf(2024) }
    var selectedMonth by remember { mutableStateOf(1) }
    var selectedDay by remember { mutableStateOf(1) }
    var selectedGregorianDate by remember { mutableStateOf(LocalDate.now()) }

    // Track if user needs to grant location permission
    var showLocationRationale by remember { mutableStateOf(false) }
    
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            showLocationRationale = false
            requestLocation(vm, context)
        }
    }

    LaunchedEffect(Unit) {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation || hasCoarseLocation) {
            requestLocation(vm, context)
        } else {
            // Don't auto-request; show rationale first
            showLocationRationale = true
        }
    }

    // Check periodically if we've crossed sunset and need to refresh the date
    // Check every minute to catch sunset transitions without being too aggressive
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60000) // Check every minute
            // Trigger refresh to check if sunset has passed
            vm.refresh()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Today", style = MaterialTheme.typography.headlineMedium)

        // Location permission rationale banner
        if (showLocationRationale) {
            LocationPermissionBanner(
                onGrantPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onDismiss = { showLocationRationale = false }
            )
        }

        // Widget promotion banner
        if (state.showWidgetBanner) {
            WidgetPromoBanner(
                onTap = { onNavigateToWidgetShowcase() },
                onDismiss = { vm.dismissWidgetBanner() }
            )
        }

        if (state.isLoading) {
            // Skeleton loading state
            SkeletonCard()
            SkeletonCard()
            SkeletonCard()
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(state.lunarLabel, style = MaterialTheme.typography.titleLarge)
                    
                    // Show both daytime and sunset dates
                    state.gregorianDaytimeDate?.let { daytime ->
                        Text("Daytime Date: $daytime", style = MaterialTheme.typography.bodyMedium)
                    }
                    state.gregorianSunsetDate?.let { sunset ->
                        Text("Sunset Date: $sunset", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (state.isAfterSunset) {
                        Text("(Currently after sunset - using sunset date)", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("(Currently before sunset - using daytime date)", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    state.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                    Spacer(Modifier.height(8.dp))

                    if (!state.hasAnchor) {
                        Button(onClick = { 
                            // Get current date from repository
                            val repo = LunarRepository(context)
                            scope.launch {
                                val today = repo.getToday()
                                if (today != null) {
                                    selectedYear = today.yearNumber
                                    selectedMonth = today.monthNumber
                                    selectedDay = today.dayOfMonth
                                    selectedGregorianDate = java.time.LocalDate.now()
                                }
                            }
                            showDatePicker = true 
                        }) {
                            Text("Set Current Date", color = androidx.compose.ui.graphics.Color.White)
                        }
                    } else if (state.showNextMonthButton) {
                        Button(onClick = { vm.confirmNextMonthStartsTomorrow() }) {
                            Text("Renewed Moon Sighted: Next Month Starts at Sundown", color = androidx.compose.ui.graphics.Color.White)
                        }
                    }
                }
            }

            // Sunset countdown
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Next Day Starts", style = MaterialTheme.typography.titleMedium)
                    if (state.isLoadingSunset || state.sunsetInfo == null) {
                        Text("Loading Sunset Data...", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        state.sunsetInfo?.let { sunsetInfo ->
                            // Update countdown display every second using LaunchedEffect
                            // This avoids updating the entire state every second
                            var displayCountdown by remember { mutableStateOf(sunsetInfo.getCountdownText()) }
                            LaunchedEffect(sunsetInfo.nextSunset) {
                                while (true) {
                                    displayCountdown = sunsetInfo.getCountdownText()
                                    kotlinx.coroutines.delay(1000)
                                }
                            }
                            Text("Countdown to sunset: $displayCountdown", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Sunset: ${sunsetInfo.nextSunset?.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                sunsetInfo.timeZone?.let {
                                    Text("Timezone: $it", style = MaterialTheme.typography.bodySmall)
                                }
                                sunsetInfo.location?.let {
                                    Text("Location: $it", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            // Jerusalem Time card
            state.jerusalemTimeInfo?.let { jerusalemInfo ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Jerusalem Time", style = MaterialTheme.typography.titleMedium)
                        
                        // Current time in Jerusalem
                        var displayCurrentTime by remember { mutableStateOf(jerusalemInfo.currentTime.format(DateTimeFormatter.ofPattern("h:mm:ss a"))) }
                        LaunchedEffect(jerusalemInfo.currentTime) {
                            while (true) {
                                val now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Jerusalem"))
                                displayCurrentTime = now.format(DateTimeFormatter.ofPattern("h:mm:ss a"))
                                kotlinx.coroutines.delay(1000)
                            }
                        }
                        Text("Current time: $displayCurrentTime", style = MaterialTheme.typography.bodyLarge)
                        
                        // Sunset time and countdown
                        jerusalemInfo.sunsetTime?.let { sunsetTime ->
                            var displayCountdown by remember { mutableStateOf(jerusalemInfo.getCountdownText()) }
                            LaunchedEffect(sunsetTime) {
                                while (true) {
                                    displayCountdown = jerusalemInfo.getCountdownText()
                                    kotlinx.coroutines.delay(1000)
                                }
                            }
                            Text("Countdown to sunset: $displayCountdown", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Sunset: ${sunsetTime.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } ?: run {
                            Text("Sunset: Unable to calculate", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

        // Upcoming feasts
        if (!state.isLoading) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Upcoming Feasts (30 days)", style = MaterialTheme.typography.titleMedium)
                    Divider()
                    if (state.isLoadingFeasts) {
                        Text("Loading Feast Data...", style = MaterialTheme.typography.bodyMedium)
                    } else if (state.upcomingFeasts.isEmpty()) {
                        Text("No upcoming feasts in the next 30 days", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        state.upcomingFeasts.take(10).forEach { feast ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(feast.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${feast.daysUntil} ${if (feast.daysUntil == 1L) "day" else "days"} (${feast.date.format(DateTimeFormatter.ofPattern("MMM d"))} at sunset)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismiss = { showDatePicker = false },
                onConfirm = { year, month, day ->
                    // Use the biblical reference date (tomorrow if after sunset)
                    val referenceDate = state.gregorianDaytimeDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
                    vm.setCurrentDate(year, month, day, referenceDate)
                    showDatePicker = false
                },
                initialYear = selectedYear,
                initialMonth = selectedMonth,
                initialDay = selectedDay,
                isAfterSunset = state.isAfterSunset,
                daytimeDate = state.gregorianDaytimeDate,
                sunsetDate = state.gregorianSunsetDate
            )
        }
    }
}

@Composable
fun DatePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit,
    initialYear: Int,
    initialMonth: Int,
    initialDay: Int,
    isAfterSunset: Boolean,
    daytimeDate: String?,
    sunsetDate: String?
) {
    val context = LocalContext.current
    val repo = remember { LunarRepository(context) }
    val defaultYear = remember { repo.calculateDefaultYear() }
    
    // Use string states to allow intermediate values while typing
    val initialYearValue = if (initialYear == 2024) {
        repo.calculateDefaultYearForMonth(initialMonth)
    } else {
        initialYear
    }
    var yearText by remember(initialYearValue) { mutableStateOf(initialYearValue.toString()) }
    var monthText by remember(initialMonth) { mutableStateOf(initialMonth.toString()) }
    var dayText by remember(initialDay) { mutableStateOf(initialDay.toString()) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Current Date") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Biblical Date:", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.TextField(
                        value = yearText,
                        onValueChange = { newValue ->
                            // Only allow digits, allow empty string while typing
                            if (newValue.all { it.isDigit() }) {
                                yearText = newValue
                            }
                        },
                        label = { Text("Year") },
                        modifier = Modifier.width(100.dp),
                        readOnly = false
                    )
                    androidx.compose.material3.TextField(
                        value = monthText,
                        onValueChange = { newValue ->
                            // Only allow digits, allow empty string while typing
                            if (newValue.all { it.isDigit() }) {
                                monthText = newValue
                                val parsedMonth = newValue.toIntOrNull()?.coerceIn(1, 13) ?: 1
                                val newDefaultYear = repo.calculateDefaultYearForMonth(parsedMonth).toString()
                                if (yearText == initialYearValue.toString() || yearText == defaultYear.toString()) {
                                    yearText = newDefaultYear
                                }
                            }
                        },
                        label = { Text("Month") },
                        modifier = Modifier.width(80.dp),
                        readOnly = false
                    )
                    androidx.compose.material3.TextField(
                        value = dayText,
                        onValueChange = { newValue ->
                            // Only allow digits, allow empty string while typing
                            if (newValue.all { it.isDigit() }) {
                                dayText = newValue
                            }
                        },
                        label = { Text("Day") },
                        modifier = Modifier.width(80.dp),
                        readOnly = false
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Gregorian Date:", style = MaterialTheme.typography.titleMedium)
                daytimeDate?.let {
                    androidx.compose.material3.TextField(
                        value = "Daytime: $it",
                        onValueChange = { },
                        label = { Text("Daytime Date") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true
                    )
                }
                sunsetDate?.let {
                    androidx.compose.material3.TextField(
                        value = "Sunset: $it",
                        onValueChange = { },
                        label = { Text("Sunset Date") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isAfterSunset) {
                        "Using daytime date (tomorrow) for the current biblical day"
                    } else {
                        "Using daytime date (today) for the current biblical day"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                // Parse and validate values on confirm
                val parsedMonth = monthText.toIntOrNull()?.coerceIn(1, 13) ?: 1
                val parsedYear = yearText.toIntOrNull() ?: repo.calculateDefaultYearForMonth(parsedMonth)
                val parsedDay = dayText.toIntOrNull()?.coerceIn(1, 30) ?: 1
                onConfirm(parsedYear, parsedMonth, parsedDay)
            }) {
                Text("Set", color = androidx.compose.ui.graphics.Color.White)
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel", color = androidx.compose.ui.graphics.Color.White)
            }
        }
    )
}

@Composable
private fun LocationPermissionBanner(
    onGrantPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Location Needed for Sunset Times",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "The biblical day begins at sunset. Grant location access to calculate accurate sunset times for your area.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        "Your location stays on your device and is never transmitted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Button(
                onClick = onGrantPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Location Access", color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}

@Composable
private fun WidgetPromoBanner(
    onTap: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Add a Widget",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "See the biblical date on your home screen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun SkeletonCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(24.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        RoundedCornerShape(4.dp)
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(16.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        RoundedCornerShape(4.dp)
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(16.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

private fun requestLocation(vm: TodayViewModel, context: android.content.Context) {
    try {
        val fusedLocationClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)
        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location: Location? ->
            vm.setLocation(location)
        }
    } catch (e: SecurityException) {
        // Permission not granted
    }
}

