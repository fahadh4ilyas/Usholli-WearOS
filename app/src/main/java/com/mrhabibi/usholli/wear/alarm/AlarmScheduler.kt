package com.mrhabibi.usholli.wear.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mrhabibi.usholli.wear.data.AppSettings
import com.mrhabibi.usholli.wear.data.NotificationType
import com.mrhabibi.usholli.wear.data.ScheduleRepository
import com.mrhabibi.usholli.wear.data.ScheduleUtil
import com.mrhabibi.usholli.wear.data.SettingsStore
import java.time.LocalDateTime
import java.time.ZoneId

object AlarmScheduler {

    const val ACTION_ADZAN = "com.mrhabibi.usholli.wear.action.ADZAN"
    const val ACTION_REMINDER = "com.mrhabibi.usholli.wear.action.REMINDER"
    const val ACTION_DAILY_UPDATE = "com.mrhabibi.usholli.wear.action.DAILY_UPDATE"

    private const val REQ_ADZAN = 100
    private const val REQ_REMINDER = 200
    private const val REQ_DAILY = 300

    fun reschedule(context: Context) {
        val store = SettingsStore(context)
        val settings = store.load()
        val schedule = ScheduleRepository(context).loadCachedSchedule()

        cancelAll(context)

        if (!settings.hasLocation || schedule == null) return

        val now = LocalDateTime.now()
        val next = ScheduleUtil.nextPrayer(schedule, settings, now) ?: return

        scheduleAdzan(context, next, settings)

        // Skip the reminder entirely when notifications are off for this period.
        if (settings.notifTypeFor(next.period) != NotificationType.NOTHING) {
            val reminderMs = settings.reminderFor(next.period)
            if (reminderMs > 0) {
                scheduleReminder(context, next, reminderMs)
            }
        }

        scheduleDailyUpdate(context)
    }

    private fun scheduleAdzan(context: Context, next: ScheduleUtil.PrayerTime, settings: AppSettings) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_ADZAN
            putExtra("period_id", next.period.id)
            putExtra("period_name", next.period.id)
            putExtra("period_hour", next.hour)
            putExtra("period_minute", next.minute)
        }
        setExact(context, next.at.toEpochMillis(), REQ_ADZAN, intent, useAlarmClock = settings.alarmMode)
    }

    private fun scheduleReminder(context: Context, next: ScheduleUtil.PrayerTime, reminderMs: Long) {
        val reminderMinutes = (reminderMs / 60_000L).toInt()
        val at = next.at.minusMinutes(reminderMinutes.toLong())
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra("period_id", next.period.id)
            putExtra("period_name", next.period.id)
            putExtra("period_hour", next.hour)
            putExtra("period_minute", next.minute)
            putExtra("reminder_minutes", reminderMinutes)
        }
        setExact(context, at.toEpochMillis(), REQ_REMINDER, intent, useAlarmClock = false)
    }

    private fun scheduleDailyUpdate(context: Context) {
        val now = LocalDateTime.now()
        val nextRun = now.toLocalDate().plusDays(1).atTime(3, 0)
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_DAILY_UPDATE
        }
        setExact(context, nextRun.toEpochMillis(), REQ_DAILY, intent, useAlarmClock = false)
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        val requests = mapOf(
            REQ_ADZAN to ACTION_ADZAN,
            REQ_REMINDER to ACTION_REMINDER,
            REQ_DAILY to ACTION_DAILY_UPDATE,
        )
        for ((requestCode, action) in requests) {
            val intent = Intent(context, NotificationReceiver::class.java).setAction(action)
            val pi = PendingIntent.getBroadcast(context, requestCode, intent, flags)
            if (pi != null) am.cancel(pi)
        }
    }

    private fun setExact(
        context: Context,
        triggerAtMillis: Long,
        requestCode: Int,
        intent: Intent,
        useAlarmClock: Boolean,
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(context, requestCode, intent, flags)

        if (useAlarmClock && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, null), pi)
                return
            } catch (_: Exception) {
                // fall through
            }
        }

        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } catch (_: SecurityException) {
            // No SCHEDULE_EXACT_ALARM permission — degrade gracefully.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, null), pi)
                } catch (_: Exception) {
                    am.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, 5 * 60_000L, pi)
                }
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        }
    }

    private fun LocalDateTime.toEpochMillis(): Long =
        atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
