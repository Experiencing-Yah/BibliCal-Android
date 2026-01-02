package com.experiencingyah.bibliCal.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.experiencingyah.bibliCal.ui.nav.BiblicalMonthAppRoot

// Royal blue color scheme
val RoyalBlue = Color(0xFF1E3A8A) // Royal blue
val RoyalBlueLight = Color(0xFF3B5BA8)
val RoyalBlueDark = Color(0xFF0F1F4A)

class MainActivity : ComponentActivity() {
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
                    BiblicalMonthAppRoot()
                }
            }
        }
    }
}

