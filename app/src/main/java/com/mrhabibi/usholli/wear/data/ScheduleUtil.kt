package com.mrhabibi.usholli.wear.data

import java.time.LocalDate
import java.time.LocalDateTime

object ScheduleUtil {

    data class PrayerTime(
        val period: Period,
        val date: String, // yyyy-MM-dd
        val hour: Int,
        val minute: Int,
    ) {
        val at: LocalDateTime
            get() {
                val (y, m, d) = date.split("-").map { it.toInt() }
                return LocalDateTime.of(y, m, d, hour, minute)
            }
    }

    /** The next prayer strictly after [now], considering only periods the user has enabled. */
    fun nextPrayer(
        schedule: Schedule,
        settings: AppSettings,
        now: LocalDateTime = LocalDateTime.now(),
    ): PrayerTime? {
        val shown = Period.all.filter { settings.isShown(it) }
        val today = now.toLocalDate()

        schedule.day(today.toString())?.let { day ->
            for (period in shown) {
                val t = day.timeOf(period) ?: continue
                val (h, m) = parseTime(t)
                val at = today.atTime(h, m)
                if (at.isAfter(now)) return PrayerTime(period, today.toString(), h, m)
            }
        }

        val tomorrow = today.plusDays(1)
        schedule.day(tomorrow.toString())?.let { day ->
            for (period in shown) {
                val t = day.timeOf(period) ?: continue
                val (h, m) = parseTime(t)
                return PrayerTime(period, tomorrow.toString(), h, m)
            }
        }
        return null
    }

    /** Today's schedule entries for the enabled periods, in period order. */
    fun todayEntries(
        schedule: Schedule,
        settings: AppSettings,
        today: LocalDate = LocalDate.now(),
    ): List<Pair<Period, String>> {
        val day = schedule.day(today.toString()) ?: return emptyList()
        return Period.all
            .filter { settings.isShown(it) }
            .mapNotNull { period -> day.timeOf(period)?.let { period to it } }
    }

    /** The period that is currently active (the one whose time window we are inside). */
    fun currentPeriod(
        schedule: Schedule,
        settings: AppSettings,
        now: LocalDateTime = LocalDateTime.now(),
    ): Period {
        val shown = Period.all.filter { settings.isShown(it) }
        val today = now.toLocalDate()
        val todayDay = schedule.day(today.toString())
        val yesterdayDay = schedule.day(today.minusDays(1).toString())

        // The "current" period is the latest period whose time has passed today,
        // or the last period of yesterday if none have passed yet today.
        var active = shown.lastOrNull() ?: Period.ISYA

        todayDay?.let { day ->
            for (period in shown) {
                val t = day.timeOf(period) ?: continue
                val (h, m) = parseTime(t)
                val at = today.atTime(h, m)
                if (!at.isAfter(now)) active = period
            }
        } ?: run {
            // No schedule for today yet — fall back to last period of yesterday.
            yesterdayDay?.let { day ->
                for (period in shown) {
                    if (day.timeOf(period) != null) active = period
                }
            }
        }
        return active
    }

    fun parseTime(hhmm: String): Pair<Int, Int> {
        val parts = hhmm.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return hour to minute
    }
}
