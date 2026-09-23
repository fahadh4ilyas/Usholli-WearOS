package com.mrhabibi.usholli.wear.data

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.mrhabibi.usholli.wear.R

/**
 * Notification types. The full "Adzan" type from the phone app is intentionally
 * removed — the Wear app only offers the six types below.
 */
object NotificationType {
    const val NOTHING = "nothing"
    const val SILENT = "silent"
    const val VIBRATE = "vibrate"
    const val DEFAULT = "default"
    const val RINGTONE = "ringtone"
    const val TAKBIR = "takbir"

    val all: List<String> =
        listOf(NOTHING, SILENT, VIBRATE, DEFAULT, RINGTONE, TAKBIR)

    @StringRes
    fun label(type: String): Int = when (type) {
        NOTHING -> R.string.notif_type_nothing
        SILENT -> R.string.notif_type_silent
        VIBRATE -> R.string.notif_type_vibrate
        DEFAULT -> R.string.notif_type_default
        RINGTONE -> R.string.notif_type_ringtone
        TAKBIR -> R.string.notif_type_takbir
        else -> R.string.notif_type_default
    }
}

/**
 * A prayer period. `apiField` maps to the field name in the myquran v3 API
 * (`subuh`, `terbit`, `dzuhur`, ...), while `id` is the stable local identifier.
 */
enum class Period(
    val id: String,
    val apiField: String,
    @StringRes val labelRes: Int,
    @StringRes val shortLabelRes: Int,
    val canAdzan: Boolean,
    val obligation: Boolean,
    val defaultNotifType: String,
    val defaultReminderMs: Long,
    val shownByDefault: Boolean,
    val skyColor: Color,
    val skyDarkColor: Color,
    val sunMoonColor: Color,
) {
    IMSAK(
        "imsak", "imsak", R.string.period_imsak, R.string.period_short_imsak,
        canAdzan = false, obligation = false,
        defaultNotifType = NotificationType.DEFAULT, defaultReminderMs = 10 * 60_000L,
        shownByDefault = true,
        skyColor = Color(0xFF212121), skyDarkColor = Color(0xFF424242), sunMoonColor = Color(0xFFEDF6E1),
    ),
    SHUBUH(
        "shubuh", "subuh", R.string.period_shubuh, R.string.period_short_shubuh,
        canAdzan = true, obligation = true,
        defaultNotifType = NotificationType.TAKBIR, defaultReminderMs = 10 * 60_000L,
        shownByDefault = true,
        skyColor = Color(0xFF1A237E), skyDarkColor = Color(0xFF3F51B5), sunMoonColor = Color(0xFFEDF6E1),
    ),
    TERBIT(
        "terbit", "terbit", R.string.period_terbit, R.string.period_short_terbit,
        canAdzan = false, obligation = false,
        defaultNotifType = NotificationType.SILENT, defaultReminderMs = 0L,
        shownByDefault = true,
        skyColor = Color(0xFF9FA8DA), skyDarkColor = Color(0xFFEE856A), sunMoonColor = Color(0xFFEDF6E1),
    ),
    DHUHA(
        "dhuha", "dhuha", R.string.period_dhuha, R.string.period_short_dhuha,
        canAdzan = false, obligation = false,
        defaultNotifType = NotificationType.NOTHING, defaultReminderMs = 0L,
        shownByDefault = true,
        skyColor = Color(0xFF5C6BC0), skyDarkColor = Color(0xFF9FA8DA), sunMoonColor = Color(0xFFFFF7C4),
    ),
    DZUHUR(
        "dzuhur", "dzuhur", R.string.period_dzuhur, R.string.period_short_dzuhur,
        canAdzan = true, obligation = true,
        defaultNotifType = NotificationType.TAKBIR, defaultReminderMs = 10 * 60_000L,
        shownByDefault = true,
        skyColor = Color(0xFF42A5F5), skyDarkColor = Color(0xFF90CAF9), sunMoonColor = Color(0xFFFFF7C4),
    ),
    ASHAR(
        "ashar", "ashar", R.string.period_ashar, R.string.period_short_ashar,
        canAdzan = true, obligation = true,
        defaultNotifType = NotificationType.TAKBIR, defaultReminderMs = 10 * 60_000L,
        shownByDefault = true,
        skyColor = Color(0xFF1E88E5), skyDarkColor = Color(0xFF0D47A1), sunMoonColor = Color(0xFFFDD10D),
    ),
    MAGHRIB(
        "maghrib", "maghrib", R.string.period_maghrib, R.string.period_short_maghrib,
        canAdzan = true, obligation = true,
        defaultNotifType = NotificationType.TAKBIR, defaultReminderMs = 10 * 60_000L,
        shownByDefault = true,
        skyColor = Color(0xFF4A148C), skyDarkColor = Color(0xFF8E24AA), sunMoonColor = Color(0xFFDC1A0B),
    ),
    ISYA(
        "isya", "isya", R.string.period_isya, R.string.period_short_isya,
        canAdzan = true, obligation = true,
        defaultNotifType = NotificationType.TAKBIR, defaultReminderMs = 10 * 60_000L,
        shownByDefault = true,
        skyColor = Color(0xFF040D18), skyDarkColor = Color(0xFF004366), sunMoonColor = Color(0xFFF5F6FC),
    );

    companion object {
        val all: List<Period> = entries

        fun byId(id: String): Period? = entries.firstOrNull { it.id == id }
    }
}
