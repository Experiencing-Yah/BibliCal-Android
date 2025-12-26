package com.example.biblicalmonth.ui.screens

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
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
import com.example.biblicalmonth.data.LunarRepository
import com.example.biblicalmonth.ui.vm.TodayViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(vm: TodayViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedYear by remember { mutableStateOf(2024) }
    var selectedMonth by remember { mutableStateOf(1) }
    var selectedDay by remember { mutableStateOf(1) }
    var selectedGregorianDate by remember { mutableStateOf(LocalDate.now()) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
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
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Auto-refresh every 10 seconds
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10000) // 10 seconds
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
                            Text("Countdown to sunset: ${sunsetInfo.countdownText}", style = MaterialTheme.typography.bodyLarge)
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
                onConfirm = { year, month, day, useSunsetDate ->
                    vm.setCurrentDate(year, month, day, useSunsetDate)
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
    onConfirm: (Int, Int, Int, Boolean) -> Unit,
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
    
    // Get current date from repository
    var year by remember { mutableStateOf(initialYear) }
    var month by remember { mutableStateOf(initialMonth) }
    var day by remember { mutableStateOf(initialDay) }
    var useSunsetDate by remember { mutableStateOf(isAfterSunset) }
    
    LaunchedEffect(Unit) {
        val currentDate = repo.getToday()
        if (currentDate != null) {
            year = currentDate.yearNumber
            month = currentDate.monthNumber
            day = currentDate.dayOfMonth
        } else if (year == 2024) {
            year = defaultYear
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Current Date") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Biblical Date:", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.TextField(
                        value = year.toString(),
                        onValueChange = { year = it.toIntOrNull() ?: defaultYear },
                        label = { Text("Year") },
                        modifier = Modifier.width(100.dp),
                        readOnly = false
                    )
                    androidx.compose.material3.TextField(
                        value = month.toString(),
                        onValueChange = { month = it.toIntOrNull()?.coerceIn(1, 13) ?: 1 },
                        label = { Text("Month") },
                        modifier = Modifier.width(80.dp),
                        readOnly = false
                    )
                    androidx.compose.material3.TextField(
                        value = day.toString(),
                        onValueChange = { day = it.toIntOrNull()?.coerceIn(1, 30) ?: 1 },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        if (useSunsetDate) "Using sunset date (evening)" else "Using daytime date",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    androidx.compose.material3.Switch(
                        checked = useSunsetDate,
                        onCheckedChange = { useSunsetDate = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(year, month, day, useSunsetDate)
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

