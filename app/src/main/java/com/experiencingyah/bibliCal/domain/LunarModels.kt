package com.experiencingyah.bibliCal.domain

import java.time.LocalDate

data class LunarDate(
    val yearNumber: Int,
    val monthNumber: Int,
    val dayOfMonth: Int,
    val monthStatus: MonthStatus,
    val monthStart: LocalDate,
) {
    init {
        require(monthNumber in 1..13)
        require(dayOfMonth in 1..30)
    }
}

enum class MonthStatus {
    CONFIRMED,
    PROJECTED,
}

data class LunarMonth(
    val yearNumber: Int,
    val monthNumber: Int,
    val startDate: LocalDate,
    val lengthDays: Int,
    val status: MonthStatus,
)

data class FeastDay(
    val title: String,
    val date: LocalDate,
    val yearNumber: Int,
    val monthNumber: Int,
    val dayOfMonth: Int,
)

