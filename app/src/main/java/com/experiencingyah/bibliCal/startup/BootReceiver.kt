package com.experiencingyah.bibliCal.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.experiencingyah.bibliCal.work.AppSchedulers

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AppSchedulers.ensureScheduled(context)
    }
}

