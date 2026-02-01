package com.experiencingyah.bibliCal.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.experiencingyah.bibliCal.R
import com.experiencingyah.bibliCal.widgets.CombinedWidgetProvider
import com.experiencingyah.bibliCal.widgets.DateWidgetProvider
import com.experiencingyah.bibliCal.widgets.ShabbatWidgetProvider

data class WidgetInfo(
    val name: String,
    val description: String,
    val previewResId: Int,
    val providerClass: Class<*>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetShowcaseScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val appWidgetManager = AppWidgetManager.getInstance(context)
    
    val widgets = listOf(
        WidgetInfo(
            name = "Combined Widget",
            description = "Shows biblical date, Gregorian date range, and Shabbat countdown all in one widget.",
            previewResId = R.drawable.widget_combined_preview,
            providerClass = CombinedWidgetProvider::class.java
        ),
        WidgetInfo(
            name = "Date Widget",
            description = "Compact widget showing just the biblical date and Gregorian date range.",
            previewResId = R.drawable.widget_date_preview,
            providerClass = DateWidgetProvider::class.java
        ),
        WidgetInfo(
            name = "Shabbat Widget",
            description = "Shows countdown to the next Shabbat.",
            previewResId = R.drawable.widget_shabbat_preview,
            providerClass = ShabbatWidgetProvider::class.java
        )
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Widgets") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            widgets.forEach { widget ->
                WidgetCard(
                    widget = widget,
                    onAddClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            // Android 8.0+ - use pin widget API
                            val canPin = appWidgetManager.isRequestPinAppWidgetSupported
                            if (canPin) {
                                val provider = ComponentName(context, widget.providerClass)
                                appWidgetManager.requestPinAppWidget(provider, null, null)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Your launcher doesn't support adding widgets directly. Long-press your home screen to add widgets manually.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            // Older Android - show instructions
                            Toast.makeText(
                                context,
                                "Long-press your home screen, tap Widgets, and find BibliCal.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
            
            // Manual instructions fallback
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Adding widgets manually",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "1. Long-press on your home screen\n" +
                        "2. Tap \"Widgets\"\n" +
                        "3. Find \"BibliCal\" in the list\n" +
                        "4. Drag a widget to your home screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetCard(
    widget: WidgetInfo,
    onAddClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = widget.previewResId),
                contentDescription = "${widget.name} preview",
                modifier = Modifier.size(80.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    widget.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    widget.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onAddClick
                ) {
                    Text("Add to Home Screen")
                }
            }
        }
    }
}
