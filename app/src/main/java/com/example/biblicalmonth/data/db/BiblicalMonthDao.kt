package com.example.biblicalmonth.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BiblicalMonthDao {
    @Query("SELECT * FROM month_starts ORDER BY startEpochDay ASC")
    suspend fun getAllMonthStarts(): List<MonthStartEntity>

    @Query("SELECT * FROM month_starts WHERE startEpochDay <= :epochDay ORDER BY startEpochDay DESC LIMIT 1")
    suspend fun getLatestStartOnOrBefore(epochDay: Long): MonthStartEntity?

    @Query("SELECT * FROM month_starts WHERE yearNumber = :year AND monthNumber = :month LIMIT 1")
    suspend fun getByYearMonth(year: Int, month: Int): MonthStartEntity?

    @Query("SELECT * FROM month_starts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MonthStartEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMonthStart(entity: MonthStartEntity): Long

    @Query("DELETE FROM month_starts")
    suspend fun deleteAllMonthStarts()

    @Query("SELECT * FROM year_decisions WHERE yearNumber = :year LIMIT 1")
    suspend fun getYearDecision(year: Int): YearDecisionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertYearDecision(entity: YearDecisionEntity)
}

