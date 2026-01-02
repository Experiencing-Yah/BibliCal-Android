package com.experiencingyah.bibliCal.util

import com.experiencingyah.bibliCal.data.settings.MonthNamingMode

object MonthNames {
    fun format(monthNumber: Int, mode: MonthNamingMode): String {
        return when (mode) {
            MonthNamingMode.NUMBERED -> "Month $monthNumber"
            MonthNamingMode.ORDINAL -> ordinal(monthNumber)
        }
    }

    private fun ordinal(n: Int): String {
        val words = listOf(
            "First",
            "Second",
            "Third",
            "Fourth",
            "Fifth",
            "Sixth",
            "Seventh",
            "Eighth",
            "Ninth",
            "Tenth",
            "Eleventh",
            "Twelfth",
            "Thirteenth",
        )
        return words.getOrNull(n - 1) ?: "Month $n"
    }
}

