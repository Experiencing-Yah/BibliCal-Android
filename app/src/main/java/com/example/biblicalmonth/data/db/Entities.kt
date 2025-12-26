package com.example.biblicalmonth.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "month_starts",
    indices = [Index(value = ["yearNumber", "monthNumber"], unique = true)]
)
data class MonthStartEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearNumber: Int,
    val monthNumber: Int, // 1..13
    val startEpochDay: Long, // LocalDate.toEpochDay()
    val confirmed: Boolean = true,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "year_decisions")
data class YearDecisionEntity(
    @PrimaryKey val yearNumber: Int,
    val barleyAviv: Boolean?, // null = unknown
    val decidedEpochDay: Long?,
)

