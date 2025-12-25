package com.example.biblicalmonth.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.biblicalmonth.work.AppSchedulers

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AppSchedulers.ensureScheduled(context)
    }
}

