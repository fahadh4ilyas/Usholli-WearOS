package com.mrhabibi.usholli.wear.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.mrhabibi.usholli.wear.complication.NextPrayerComplicationService
import com.mrhabibi.usholli.wear.tile.UsholliTileService

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Keep the device awake briefly while we post the notification.
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "usholli:notif").acquire(10_000L)
        }

        when (intent.action) {
            AlarmScheduler.ACTION_ADZAN -> {
                val periodId = intent.getStringExtra("period_id") ?: return
                Notifications.showAdzan(context, periodId)
                // The next prayer has changed, so refresh the tile and complication
                // (otherwise they stay "stuck" showing the prayer that just passed).
                UsholliTileService.requestUpdate(context)
                NextPrayerComplicationService.requestUpdate(context)
                // Re-schedule the next prayer (using the cached schedule).
                AlarmScheduler.reschedule(context)
            }
            AlarmScheduler.ACTION_REMINDER -> {
                val periodId = intent.getStringExtra("period_id") ?: return
                val minutes = intent.getIntExtra("reminder_minutes", 0)
                Notifications.showReminder(context, periodId, minutes)
            }
            AlarmScheduler.ACTION_DAILY_UPDATE -> {
                AlarmScheduler.reschedule(context)
            }
        }
    }
}
