package com.example.biblicalmonth.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MonthStartEntity::class,
        YearDecisionEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class BiblicalMonthDatabase : RoomDatabase() {
    abstract fun dao(): BiblicalMonthDao

    companion object {
        @Volatile private var INSTANCE: BiblicalMonthDatabase? = null

        fun get(context: Context): BiblicalMonthDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BiblicalMonthDatabase::class.java,
                    "biblical_month.db"
                ).build().also { INSTANCE = it }
            }
    }
}

