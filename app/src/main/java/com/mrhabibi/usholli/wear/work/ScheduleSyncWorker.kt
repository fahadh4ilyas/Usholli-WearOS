package com.mrhabibi.usholli.wear.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mrhabibi.usholli.wear.alarm.AlarmScheduler
import com.mrhabibi.usholli.wear.data.ScheduleRepository
import com.mrhabibi.usholli.wear.data.SettingsStore
import java.util.concurrent.TimeUnit

/**
 * Daily background sync: fetches a fresh schedule (covering well over 7 days ahead)
 * and re-schedules prayer alarms. Runs only while a network connection is available,
 * so the cached schedule is always kept fresh for offline use.
 */
class ScheduleSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = ScheduleRepository(applicationContext)

        // Cache the next 7 days of Hijri dates (independent of location).
        repo.fetchAndCacheHijriRange()

        val settings = SettingsStore(applicationContext).load()
        if (!settings.hasLocation) return Result.success()

        val schedule = repo.fetchAndCacheSchedule(settings.regionId)

        return if (schedule != null) {
            AlarmScheduler.reschedule(applicationContext)
            Result.success()
        } else {
            Result.retry()
        }
    }
}

object WorkScheduler {
    private const val UNIQUE_NAME = "daily_schedule_sync"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
