package com.mrhabibi.usholli.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.mrhabibi.usholli.wear.MainActivity
import com.mrhabibi.usholli.wear.data.ScheduleRepository
import com.mrhabibi.usholli.wear.data.ScheduleUtil
import com.mrhabibi.usholli.wear.data.SettingsStore
import java.time.LocalDateTime

/**
 * Watch-face complication (the "small" widget) showing the next prayer name + time.
 *
 * Adapts to the slot size via the requested type:
 *  - SHORT_TEXT (small slot): short name + time.
 *  - LONG_TEXT (larger slot): full name + time.
 *
 * By default the name is the title and the time is the main text; a settings
 * toggle ("Tukar Judul & Teks") swaps them.
 */
class NextPrayerComplicationService : ComplicationDataSourceService() {

    companion object {
        /** Ask the system to re-fetch this complication's data (e.g. after a prayer passes). */
        fun requestUpdate(context: Context) {
            runCatching {
                ComplicationDataSourceUpdateRequester.create(
                    context,
                    ComponentName(context, NextPrayerComplicationService::class.java),
                ).requestUpdateAll()
            }
        }
    }

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        listener.onComplicationData(buildData(request.complicationType) ?: NoDataComplicationData())
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val description = PlainComplicationText.Builder("Shubuh 04:07").build()
        return when (type) {
            // Default: title = name, text = time.
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("04:07").build(),
                contentDescription = description,
            ).setTitle(PlainComplicationText.Builder("Sh").build()).build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("04:07").build(),
                contentDescription = description,
            ).setTitle(PlainComplicationText.Builder("Shubuh").build()).build()

            else -> null
        }
    }

    private fun buildData(type: ComplicationType): ComplicationData? {
        val settings = SettingsStore(this).load()
        if (!settings.hasLocation) return null
        val schedule = ScheduleRepository(this).loadCachedSchedule() ?: return null

        val next = ScheduleUtil.nextPrayer(schedule, settings, LocalDateTime.now()) ?: return null
        val name = getString(next.period.labelRes)
        val shortName = getString(next.period.shortLabelRes)
        val time = "%02d:%02d".format(next.hour, next.minute)
        val contentDescription = "$name $time"

        val tapAction = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Default: title = name, text = time. When the swap toggle is on,
        // title = time, text = name.
        val swap = settings.complicationSwap

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                val title = if (swap) time else shortName
                val text = if (swap) shortName else time
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text).build(),
                    contentDescription = PlainComplicationText.Builder(contentDescription).build(),
                )
                    .setTitle(PlainComplicationText.Builder(title).build())
                    .setTapAction(tapAction)
                    .build()
            }

            ComplicationType.LONG_TEXT -> {
                val title = if (swap) time else name
                val text = if (swap) name else time
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text).build(),
                    contentDescription = PlainComplicationText.Builder(contentDescription).build(),
                )
                    .setTitle(PlainComplicationText.Builder(title).build())
                    .setTapAction(tapAction)
                    .build()
            }

            else -> null
        }
    }
}
