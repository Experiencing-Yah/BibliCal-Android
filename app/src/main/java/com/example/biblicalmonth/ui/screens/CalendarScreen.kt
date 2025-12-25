package com.example.biblicalmonth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.biblicalmonth.ui.vm.CalendarViewModel

@Composable
fun CalendarScreen(vm: CalendarViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(state.title, style = MaterialTheme.typography.headlineSmall)
        state.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.prevMonth() }) { Text("Prev") }
            Button(onClick = { vm.nextMonth() }) { Text("Next") }
        }

        val weekLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            weekLabels.forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
        }

        Card {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.weeks.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        week.forEach { cell ->
                            if (cell == null) {
                                Spacer(Modifier.size(44.dp))
                            } else {
                                val bg = when {
                                    cell.isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                    cell.feastTitles.isNotEmpty() -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                                    else -> Color.Transparent
                                }
                                Column(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(bg)
                                        .padding(6.dp),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(cell.lunarDay.toString(), style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        cell.gregorianDate.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (cell.feastTitles.isNotEmpty()) {
                                        Text(
                                            cell.feastTitles.first(),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

