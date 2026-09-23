package com.mrhabibi.usholli.wear.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mrhabibi.usholli.wear.MainActivity
import com.mrhabibi.usholli.wear.R
import com.mrhabibi.usholli.wear.data.NotificationType
import com.mrhabibi.usholli.wear.data.SettingsStore

object Notifications {

    private const val CHANNEL_ID = "usholli_notifications"
    private const val CHANNEL_ID_SILENT = "usholli_notifications_silent"
    private const val NOTIF_ID_ADZAN = 1
    private const val NOTIF_ID_REMINDER = 2

    private val vibrationPattern = longArrayOf(0, 400, 200, 400)

    // Keep strong references so MediaPlayer isn't GC'd mid-playback.
    private val activePlayers = mutableSetOf<MediaPlayer>()

    /**
     * Play a sound through the media stream (not the notification stream).
     * Wear OS watches are often left in vibrate/silent mode, which mutes the
     * notification stream; the media stream still plays so the prayer sound is heard.
     */
    private fun playSoundOnMediaStream(context: Context, uri: Uri) {
        runCatching {
            val player = MediaPlayer()
            activePlayers.add(player)
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            player.setDataSource(context, uri)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener {
                activePlayers.remove(it)
                it.release()
            }
            player.setOnErrorListener { it, _, _ ->
                activePlayers.remove(it)
                it.release()
                true
            }
            player.prepareAsync()
        }
    }

    /** Create the vibrating + silent channels. Sound is played via the media stream. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Vibrating channel (default for sound/vibrate types).
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification)
                setSound(null, null)
                setVibrationPattern(vibrationPattern)
                setShowBadge(false)
            },
        )

        // Silent channel (no vibration, for the "Sunyi" type).
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID_SILENT,
                context.getString(R.string.notification),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification)
                setSound(null, null)
                setVibrationPattern(null)
                setShowBadge(false)
            },
        )
    }

    /** Show the "it is time for prayer" notification, honouring the chosen type. */
    fun showAdzan(context: Context, periodId: String) {
        val period = com.mrhabibi.usholli.wear.data.Period.byId(periodId) ?: return
        val settings = SettingsStore(context).load()
        val type = settings.notifTypeFor(period)

        if (type == NotificationType.NOTHING) return

        val name = context.getString(period.labelRes)
        val city = settings.regionName.ifEmpty { null }
        val text = city?.let { context.getString(R.string.next_prayer_city, it) }
            ?: context.getString(R.string.next_prayer_city_default)

        post(
            context,
            id = NOTIF_ID_ADZAN,
            title = context.getString(R.string.prayer_time, name),
            text = text,
            type = type,
            ringtoneUri = settings.ringtoneUri,
        )
    }

    /** Show the pre-prayer reminder notification. */
    fun showReminder(context: Context, periodId: String, minutes: Int) {
        val period = com.mrhabibi.usholli.wear.data.Period.byId(periodId) ?: return
        val settings = SettingsStore(context).load()
        val periodType = settings.notifTypeFor(period)

        // Don't remind at all when notifications are off for this period.
        if (periodType == NotificationType.NOTHING) return

        // Sound-based types (default/ringtone/takbir) keep the simple system-default
        // reminder sound. Silent/vibrate mirror the period's chosen type.
        val reminderType = when (periodType) {
            NotificationType.SILENT -> NotificationType.SILENT
            NotificationType.VIBRATE -> NotificationType.VIBRATE
            else -> NotificationType.DEFAULT
        }

        val name = context.getString(period.labelRes)
        val city = settings.regionName.ifEmpty { null }
        val text = city?.let { context.getString(R.string.next_prayer_city, it) }
            ?: context.getString(R.string.next_prayer_city_default)

        post(
            context,
            id = NOTIF_ID_REMINDER,
            title = context.getString(R.string.reminder_title, minutes, name),
            text = text,
            type = reminderType,
            ringtoneUri = settings.ringtoneUri,
        )
    }

    private fun post(
        context: Context,
        id: Int,
        title: String,
        text: String,
        type: String,
        ringtoneUri: String,
    ) {
        ensureChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Vibration is controlled by the channel on Android 8+; use the silent
        // channel for "Sunyi" so it neither vibrates nor plays a channel sound.
        val channelId = if (type == NotificationType.SILENT) CHANNEL_ID_SILENT else CHANNEL_ID

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        when (type) {
            NotificationType.SILENT -> {
                // Silent channel: no sound, no vibration.
            }
            NotificationType.VIBRATE -> {
                // Vibrating channel: vibration only (no sound).
            }
            NotificationType.DEFAULT -> {
                // Sound via media stream so it plays even in vibrate/silent mode.
                playSoundOnMediaStream(context, Settings.System.DEFAULT_NOTIFICATION_URI)
            }
            NotificationType.TAKBIR -> {
                playSoundOnMediaStream(context, resourceSound(context))
            }
            NotificationType.RINGTONE -> {
                val uri = ringtoneUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                playSoundOnMediaStream(context, uri)
            }
        }

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — ignore.
        }
    }

    private fun resourceSound(context: Context): Uri =
        Uri.parse("android.resource://${context.packageName}/${R.raw.takbir}")
}
