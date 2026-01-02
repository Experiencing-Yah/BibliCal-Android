package com.experiencingyah.bibliCal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.ui.vm.CalendarViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen(vm: CalendarViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    // Auto-refresh every 10 seconds
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10000) // 10 seconds
            vm.refresh()
        }
    }

    var projectionExpanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(state.title, style = MaterialTheme.typography.headlineSmall)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.prevMonth() }) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Previous month",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = { vm.nextMonth() }) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Next month",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // Projection options - collapsible
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Projection Options",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { projectionExpanded = !projectionExpanded }) {
                        Icon(
                            imageVector = if (projectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (projectionExpanded) "Collapse" else "Expand"
                        )
                    }
                }
                if (projectionExpanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Project extra month (13th month)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.projectExtraMonth,
                            onCheckedChange = { vm.setProjectExtraMonth(it) }
                        )
                    }
                    // Per-month projection checkbox (only for current month if projected)
                    state.projectedMonthInfos.forEach { info ->
                        // Check if this month is projected as 30 days (either user-set or auto-predicted)
                        val isChecked = state.currentMonthProjectedLength == 30
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Assume 30-Day Month: ${info.monthName}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = isChecked,
                                enabled = true,
                                onCheckedChange = { 
                                    vm.setProjectedMonthLength(info.year, info.month, if (it) 30 else 29)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Calendar with headings grouped together
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Day headers with proper spacing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    state.weeks.firstOrNull()?.forEachIndexed { index, _ ->
                        Column(
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (index == 6) {
                                Text(
                                    text = "Shabbat",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip
                                )
                            } else {
                                Text(
                                    text = "Day",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }

                // Calendar grid with lighter background box around the entire grid
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(4.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.weeks.forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                week.forEachIndexed { cellIndex, cell ->
                                    if (cell == null) {
                                        Spacer(Modifier.weight(1f).size(50.dp))
                                    } else {
                                        // Check if this is a Shabbat day (Saturday) or a rest day during feasts
                                        val isShabbat = cell.gregorianDate.dayOfWeek == DayOfWeek.SATURDAY
                                        val isRestDay = cell.feastTitles.any { title ->
                                            title.contains("Unleavened Bread begins", ignoreCase = true) ||
                                            title.contains("Unleavened Bread ends", ignoreCase = true) ||
                                            title.contains("Shavuot", ignoreCase = true) ||
                                            title.contains("Trumpets", ignoreCase = true) ||
                                            title.contains("Atonement", ignoreCase = true) ||
                                            title.contains("Tabernacles begins", ignoreCase = true) ||
                                            title.contains("Tabernacles ends", ignoreCase = true)
                                        }
                                        val isSabbathDay = isShabbat || isRestDay
                                        
                                        val bg = when {
                                            cell.isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                            cell.feastTitles.isNotEmpty() -> MaterialTheme.colorScheme.primary.copy(alpha = 0.40f) // Blue for feasts
                                            else -> Color.Transparent
                                        }
                                        
                                        val textColor = if (isSabbathDay) {
                                            Color(0xFFFFD700) // Gold color
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                        
                                        // Container for the cell with relative positioning for gregorian date box
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .size(48.dp)
                                        ) {
                                            // Gregorian date box - same width as cell, offset right by 25% to show it spans into next day
                                            // This shows that biblical day includes most of current gregorian day and part of next day (after sunset)
                                            Box(
                                                modifier = Modifier
                                                    .offset(x = 12.dp, y = 32.dp) // Offset right by 25% of cell width (48dp * 0.25 = 12dp)
                                                    .size(48.dp, 12.dp) // Same width as cell
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    cell.gregorianDate.dayOfMonth.toString(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isSabbathDay) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            
                                            // Biblical day cell
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(bg)
                                                    .padding(7.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Top,
                                            ) {
                                                Text(
                                                    cell.lunarDay.toString(),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = textColor
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

        // Feast list box
        if (state.feastsInMonth.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Feasts This Month", style = MaterialTheme.typography.titleMedium)
                    Divider()
                    state.feastsInMonth.forEach { feast ->
                        // Extract feast name without date (remove anything in parentheses)
                        val feastName = feast.title.replace(Regex("\\([^)]+\\)"), "").trim()
                        
                        // Get biblical day - use the dayOfMonth from the feast (which is now calculated in ViewModel)
                        val biblicalDay = feast.dayOfMonth
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (biblicalDay > 0) "$feastName ($biblicalDay)" else feastName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                feast.date.format(DateTimeFormatter.ofPattern("M/d/yyyy")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

