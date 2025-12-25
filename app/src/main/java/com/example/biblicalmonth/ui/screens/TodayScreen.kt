package com.example.biblicalmonth.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.biblicalmonth.ui.vm.TodayViewModel

@Composable
fun TodayScreen(vm: TodayViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Today", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxSize(),) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Lunar: ${state.lunarLabel}", style = MaterialTheme.typography.titleLarge)
                Text("Gregorian: ${state.gregorianLabel}", style = MaterialTheme.typography.bodyMedium)
                state.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                Spacer(Modifier.height(8.dp))

                if (!state.hasAnchor) {
                    Button(onClick = { vm.setAnchorToTodayMonth1Year1() }) {
                        Text("Set anchor: Month 1 Day 1 = today")
                    }
                } else {
                    Button(onClick = { vm.confirmNextMonthStartsTomorrow() }) {
                        Text("Confirm: next month starts tomorrow")
                    }
                }
            }
        }
    }
}

