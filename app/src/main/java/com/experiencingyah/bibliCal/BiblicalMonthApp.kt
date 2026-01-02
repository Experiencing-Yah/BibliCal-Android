package com.experiencingyah.bibliCal

import android.app.Application
import com.experiencingyah.bibliCal.work.AppSchedulers

class BiblicalMonthApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSchedulers.ensureScheduled(this)
    }
}

