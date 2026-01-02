package com.experiencingyah.bibliCal.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.ui.vm.WelcomeStep
import com.experiencingyah.bibliCal.ui.vm.WelcomeViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(
    navController: NavController,
    vm: WelcomeViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { LunarRepository(context) }
    
    var showDatePicker by remember { mutableStateOf(false) }
    var datePickerType by remember { mutableStateOf<DatePickerType?>(null) }
    var selectedYear by remember { mutableStateOf(repo.calculateDefaultYear()) }
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
        } else {
            vm.setLoadingLocation(false)
        }
    }
    
    // Request location when moving to estimate step
    LaunchedEffect(state.currentStep) {
        if (state.currentStep == WelcomeStep.ESTIMATE_FROM_LOCATION && state.location == null) {
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
                vm.setLoadingLocation(true)
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }
    
    // Navigate to TodayScreen after date is set
    LaunchedEffect(Unit) {
        scope.launch {
            // Check if anchor was set (by another process or if user already set it)
            if (repo.hasAnyAnchor()) {
                navController.navigate("today") {
                    popUpTo("welcome") { inclusive = true }
                }
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        
        Text(
            text = "Welcome!",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Let's set up your biblical calendar",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.height(16.dp))
        
        when (state.currentStep) {
            WelcomeStep.KNOW_BIBLICAL_DATE -> {
                Step1KnowBiblicalDate(
                    onYes = {
                        datePickerType = DatePickerType.BiblicalDate
                        selectedYear = repo.calculateDefaultYear()
                        selectedMonth = 1
                        selectedDay = 1
                        selectedGregorianDate = LocalDate.now()
                        showDatePicker = true
                    },
                    onNo = {
                        vm.setStep(WelcomeStep.KNOW_NEW_MOON_DATE)
                    }
                )
            }
            
            WelcomeStep.KNOW_NEW_MOON_DATE -> {
                Step2KnowNewMoonDate(
                    onYes = {
                        datePickerType = DatePickerType.NewMoonDate
                        selectedYear = repo.calculateDefaultYear()
                        selectedMonth = 1
                        selectedDay = 1
                        selectedGregorianDate = LocalDate.now()
                        showDatePicker = true
                    },
                    onNo = {
                        vm.setStep(WelcomeStep.ESTIMATE_FROM_LOCATION)
                    }
                )
            }
            
            WelcomeStep.ESTIMATE_FROM_LOCATION -> {
                Step3EstimateFromLocation(
                    isLoadingLocation = state.isLoadingLocation,
                    isLoadingEstimate = state.isLoadingEstimate,
                    estimatedDate = state.estimatedNewMoonDate,
                    estimatedMonth = state.estimatedMonth,
                    estimatedDay = state.estimatedDay,
                    errorMessage = state.errorMessage,
                    onUseEstimate = { year, month, day ->
                        // Use setBiblicalDate with today as reference and the edited day
                        // Pass location so it can check if it's after sunset
                        val today = LocalDate.now()
                        val location = state.location
                        vm.setBiblicalDate(year, month, day, today, location)
                        // Wait a moment for the anchor to be set, then navigate
                        scope.launch {
                            kotlinx.coroutines.delay(500)
                            navController.navigate("today") {
                                popUpTo("welcome") { inclusive = true }
                            }
                        }
                    },
                    onSetManually = {
                        datePickerType = DatePickerType.NewMoonDate
                        selectedYear = repo.calculateDefaultYear()
                        selectedMonth = 1
                        selectedDay = 1
                        selectedGregorianDate = LocalDate.now()
                        showDatePicker = true
                    }
                )
            }
        }
        
        state.errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        if (showDatePicker) {
            // Check if it's after sunset to show correct context
            val location = state.location
            var isAfterSunset = false
            var daytimeDate = selectedGregorianDate.toString()
            var sunsetDate: String? = null
            
            if (location != null) {
                val now = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault())
                val todaySunset = com.experiencingyah.bibliCal.util.SunsetCalculator.calculateSunsetTime(
                    selectedGregorianDate,
                    location.latitude,
                    location.longitude,
                    java.time.ZoneId.systemDefault()
                )
                
                if (todaySunset != null) {
                    isAfterSunset = now.isAfter(todaySunset)
                    if (isAfterSunset) {
                        // After sunset: daytime date is tomorrow, sunset date is today
                        daytimeDate = selectedGregorianDate.plusDays(1).toString()
                        sunsetDate = selectedGregorianDate.toString()
                    } else {
                        // Before sunset: daytime date is today, sunset date is yesterday
                        daytimeDate = selectedGregorianDate.toString()
                        sunsetDate = selectedGregorianDate.minusDays(1).toString()
                    }
                }
            }
            
            DatePickerDialog(
                onDismiss = { showDatePicker = false },
                onConfirm = { year, month, day ->
                    when (datePickerType) {
                        DatePickerType.BiblicalDate -> {
                            // Pass location so it can check if it's after sunset
                            vm.setBiblicalDate(year, month, day, selectedGregorianDate, location)
                        }
                        DatePickerType.NewMoonDate -> {
                            // For new moon date, the selected date is when first sliver was visible
                            // The month starts at sunset on that date
                            vm.setNewMoonDate(selectedGregorianDate, year, month)
                        }
                        null -> {}
                    }
                    showDatePicker = false
                    // Navigate after setting date
                    scope.launch {
                        kotlinx.coroutines.delay(500)
                        navController.navigate("today") {
                            popUpTo("welcome") { inclusive = true }
                        }
                    }
                },
                initialYear = selectedYear,
                initialMonth = selectedMonth,
                initialDay = selectedDay,
                isAfterSunset = isAfterSunset,
                daytimeDate = daytimeDate,
                sunsetDate = sunsetDate
            )
        }
    }
}

@Composable
private fun Step1KnowBiblicalDate(
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Do you know the current biblical date?",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "If you know the current biblical date (year, month, and day), you can set it directly.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(8.dp))
            
            Button(
                onClick = onYes,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Yes, I know the date", color = androidx.compose.ui.graphics.Color.White)
            }
            
            Button(
                onClick = onNo,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("No, I don't know", color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}

@Composable
private fun Step2KnowNewMoonDate(
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Do you know when the first sliver of the renewed moon was visible?",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "The biblical month starts when the first sliver of the new moon is visible. If you know when this last occurred, you can set that date.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(8.dp))
            
            Button(
                onClick = onYes,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Yes, I know the date", color = androidx.compose.ui.graphics.Color.White)
            }
            
            Button(
                onClick = onNo,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("No, I don't know", color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}

@Composable
private fun Step3EstimateFromLocation(
    isLoadingLocation: Boolean,
    isLoadingEstimate: Boolean,
    estimatedDate: LocalDate?,
    estimatedMonth: Int?,
    estimatedDay: Int?,
    errorMessage: String?,
    onUseEstimate: (Int, Int, Int) -> Unit,
    onSetManually: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { LunarRepository(context) }
    val defaultYear = remember { repo.calculateDefaultYear() }
    var selectedYear by remember { mutableStateOf(defaultYear) }
    var selectedMonth by remember { mutableStateOf(estimatedMonth ?: 1) }
    var selectedDay by remember { mutableStateOf(estimatedDay ?: 1) }
    
    // Update selected month and day when estimated values change
    LaunchedEffect(estimatedMonth) {
        if (estimatedMonth != null) {
            selectedMonth = estimatedMonth
        }
    }
    
    LaunchedEffect(estimatedDay) {
        if (estimatedDay != null) {
            selectedDay = estimatedDay
        }
    }
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Estimating from your location",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            
            when {
                isLoadingLocation -> {
                    CircularProgressIndicator()
                    Text(
                        text = "Requesting location permission...",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                
                isLoadingEstimate -> {
                    CircularProgressIndicator()
                    Text(
                        text = "Calculating new moon visibility...",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                
                estimatedDate != null -> {
                    Text(
                        text = "Based on your location, we estimate the first sliver of the new moon was visible on:",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = estimatedDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "This is an estimate. The month will update at the next renewed moon.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Year, month, and day selection for the estimate
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Select biblical year, month, and day:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        androidx.compose.material3.TextField(
                            value = selectedYear.toString(),
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    selectedYear = newValue.toIntOrNull() ?: defaultYear
                                }
                            },
                            label = { Text("Year") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.material3.TextField(
                                value = selectedMonth.toString(),
                                onValueChange = { newValue ->
                                    if (newValue.all { it.isDigit() }) {
                                        selectedMonth = newValue.toIntOrNull()?.coerceIn(1, 13) ?: 1
                                    }
                                },
                                label = { Text("Month (1-13)") },
                                modifier = Modifier.weight(1f)
                            )
                            
                            androidx.compose.material3.TextField(
                                value = selectedDay.toString(),
                                onValueChange = { newValue ->
                                    if (newValue.all { it.isDigit() }) {
                                        selectedDay = newValue.toIntOrNull()?.coerceIn(1, 30) ?: 1
                                    }
                                },
                                label = { Text("Day (1-30)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Button(
                        onClick = {
                            // Use the edited biblical date (year, month, day)
                            onUseEstimate(selectedYear, selectedMonth, selectedDay)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use this estimate", color = androidx.compose.ui.graphics.Color.White)
                    }
                    
                    Button(
                        onClick = onSetManually,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Set date manually instead", color = androidx.compose.ui.graphics.Color.White)
                    }
                }
                
                else -> {
                    Text(
                        text = "Unable to estimate. Please set the date manually.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Button(
                        onClick = onSetManually,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Set date manually", color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
    }
}

private enum class DatePickerType {
    BiblicalDate,
    NewMoonDate
}

private fun requestLocation(vm: WelcomeViewModel, context: android.content.Context) {
    try {
        val fusedLocationClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)
        val cancellationTokenSource = CancellationTokenSource()
        
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location: Location? ->
            vm.setLocation(location)
        }.addOnFailureListener {
            vm.setLoadingLocation(false)
        }
    } catch (e: SecurityException) {
        vm.setLoadingLocation(false)
    }
}

