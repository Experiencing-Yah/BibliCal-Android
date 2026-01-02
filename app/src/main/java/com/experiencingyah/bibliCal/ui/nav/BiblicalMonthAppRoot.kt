package com.experiencingyah.bibliCal.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.ui.screens.CalendarScreen
import com.experiencingyah.bibliCal.ui.screens.SettingsScreen
import com.experiencingyah.bibliCal.ui.screens.TodayScreen
import com.experiencingyah.bibliCal.ui.screens.WelcomeScreen
import kotlinx.coroutines.launch

@Composable
fun BiblicalMonthAppRoot() {
    val navController = rememberNavController()
    val backStack = navController.currentBackStackEntryAsState()
    val currentRoute = backStack.value?.destination?.route
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { LunarRepository(context) }
    
    var hasAnchor by remember { mutableStateOf<Boolean?>(null) }
    var startDestination by remember { mutableStateOf<String?>(null) }
    
    // Check if anchor exists on startup
    LaunchedEffect(Unit) {
        val anchorExists = repo.hasAnyAnchor()
        hasAnchor = anchorExists
        startDestination = if (anchorExists) "today" else "welcome"
    }

    val items = listOf(
        NavItem("today", "Today", Icons.Default.Today),
        NavItem("calendar", "Calendar", Icons.Default.CalendarMonth),
        NavItem("settings", "Settings", Icons.Default.Settings),
    )

    // Only show bottom bar if not on welcome screen
    val showBottomBar = currentRoute != "welcome" && currentRoute != null

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    items.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // Wait for start destination to be determined
        startDestination?.let { destination ->
            NavHost(
                navController = navController,
                startDestination = destination,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable("welcome") { WelcomeScreen(navController = navController) }
                composable("today") { TodayScreen() }
                composable("calendar") { CalendarScreen() }
                composable("settings") { SettingsScreen() }
            }
        }
    }
}

private data class NavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

