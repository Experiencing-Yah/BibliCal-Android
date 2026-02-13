package com.experiencingyah.bibliCal.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.experiencingyah.bibliCal.integrations.PassagesIntegration
import com.experiencingyah.bibliCal.ui.nav.BiblicalMonthAppRoot
import com.experiencingyah.bibliCal.ui.screens.PassagesRequest
import kotlinx.coroutines.launch

// Royal blue color scheme
val RoyalBlue = Color(0xFF1E3A8A) // Royal blue
val RoyalBlueLight = Color(0xFF3B5BA8)
val RoyalBlueDark = Color(0xFF0F1F4A)

class MainActivity : ComponentActivity() {
    private var passagesRequestState by mutableStateOf<PassagesRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkTheme = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (darkTheme) {
                    darkColorScheme(
                        primary = RoyalBlueLight,
                        secondary = RoyalBlue,
                        tertiary = RoyalBlueDark,
                    )
                } else {
                    lightColorScheme(
                        primary = RoyalBlue,
                        secondary = RoyalBlueDark,
                        tertiary = RoyalBlueLight,
                    )
                }
            ) {
                Surface {
                    BiblicalMonthAppRoot(
                        passagesRequest = passagesRequestState,
                        onSendToPassages = { sendToPassages() },
                        onPassagesDismissed = { passagesRequestState = null }
                    )
                }
            }
        }
        handlePassagesWeekRequest(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePassagesWeekRequest(intent)
    }

    private fun handlePassagesWeekRequest(intent: Intent) {
        val data = intent.data ?: return
        Log.d("PassAges", "Deep link opened: $data")
        if (data.scheme != "biblical") {
            Log.d("PassAges", "Ignoring deep link (scheme mismatch): ${data.scheme}")
            return
        }
        val requestKey = (data.host ?: data.path?.trimStart('/'))?.lowercase()
        Log.d(
            "PassAges",
            "Parsed deep link: host=${data.host}, path=${data.path}, requestKey=$requestKey"
        )

        when (requestKey) {
            "passages-request-week" -> lifecycleScope.launch {
                Log.d("PassAges", "Handling request: passages-request-week")
                val week = PassagesIntegration.getCurrentParshaWeekIndex(applicationContext)
                if (week == null) {
                    Log.d("PassAges", "Unable to resolve week index (null)")
                    val summary = PassagesIntegration.getMonthStartSummary(applicationContext)
                    Log.d(
                        "PassAges",
                        "Month start summary: count=${summary.count}, earliestStart=${summary.earliestStart}, latestStart=${summary.latestStart}"
                    )
                    val hasAnchor = summary.count > 0
                    passagesRequestState = PassagesRequest.Error(
                        if (hasAnchor) {
                            "Unable to resolve the current parsha week from stored data. Open BibliCal and refresh the date."
                        } else {
                            "No biblical date is set yet. Open BibliCal and set the current date."
                        }
                    )
                    return@launch
                }
                val name = PassagesIntegration.getParshaName(week)
                passagesRequestState = PassagesRequest.Week(index = week, name = name)
                Log.d("PassAges", "Set passagesRequestState: week=$week name=$name")
            }
            "passages-request-13th-month" -> lifecycleScope.launch {
                Log.d("PassAges", "Handling request: passages-request-13th-month")
                val has13 = PassagesIntegration.getCurrentHas13thMonth(applicationContext)
                if (has13 == null) {
                    Log.d("PassAges", "Unable to resolve 13th month flag (null)")
                    val summary = PassagesIntegration.getMonthStartSummary(applicationContext)
                    Log.d(
                        "PassAges",
                        "Month start summary: count=${summary.count}, earliestStart=${summary.earliestStart}, latestStart=${summary.latestStart}"
                    )
                    val hasAnchor = summary.count > 0
                    passagesRequestState = PassagesRequest.Error(
                        if (hasAnchor) {
                            "Unable to resolve whether this year has a 13th month from stored data. Open BibliCal and refresh the date."
                        } else {
                            "No biblical date is set yet. Open BibliCal and set the current date."
                        }
                    )
                    return@launch
                }
                passagesRequestState = PassagesRequest.ThirteenthMonth(has13 = has13)
                Log.d("PassAges", "Set passagesRequestState: has13=$has13")
            }
            else -> {
                Log.d("PassAges", "Ignoring deep link (unknown requestKey): $requestKey")
                return
            }
        }
    }

    private fun sendToPassages() {
        val request = passagesRequestState ?: return
        if (request is PassagesRequest.Error) {
            Log.d("PassAges", "Not sending callback due to error state")
            return
        }
        val callbackUri = when (request) {
            is PassagesRequest.Week -> Uri.parse("passages://set-week?week=${request.index}")
            is PassagesRequest.ThirteenthMonth -> {
                val hasValue = if (request.has13) "1" else "0"
                Uri.parse("passages://set-13th-month?has=$hasValue")
            }
            is PassagesRequest.Error -> return
        }
        Log.d("PassAges", "Sending callback to PassAges: $callbackUri")
        startActivity(Intent(Intent.ACTION_VIEW, callbackUri))
        passagesRequestState = null
    }
}

