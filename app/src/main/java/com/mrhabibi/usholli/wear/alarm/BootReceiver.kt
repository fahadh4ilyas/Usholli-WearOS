package com.mrhabibi.usholli.wear.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mrhabibi.usholli.wear.work.WorkScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                AlarmScheduler.reschedule(context)
                WorkScheduler.schedule(context)
            }
        }
    }
}
