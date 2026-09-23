package com.mrhabibi.usholli.wear

import android.app.Application
import com.mrhabibi.usholli.wear.alarm.Notifications
import com.mrhabibi.usholli.wear.work.WorkScheduler

class UsholliWearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        WorkScheduler.schedule(this)
    }
}
