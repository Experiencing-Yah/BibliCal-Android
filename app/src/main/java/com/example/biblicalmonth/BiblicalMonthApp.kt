package com.example.biblicalmonth

import android.app.Application
import com.example.biblicalmonth.work.AppSchedulers

class BiblicalMonthApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSchedulers.ensureScheduled(this)
    }
}

