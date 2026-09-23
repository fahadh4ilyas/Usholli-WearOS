package com.mrhabibi.usholli.wear.data

import android.content.Context

/** Persistent user settings (mirrors the phone app's preference + per-period notif settings). */
data class AppSettings(
    val regionId: String = "",
    val regionName: String = "",
    val regionProv: String = "",
    val autoDetect: Boolean = false,
    val alarmMode: Boolean = false,
    val showImsak: Boolean = true,
    val showTerbit: Boolean = true,
    val showDhuha: Boolean = true,
    val hijriCorrection: Int = 0,
    val notifTypes: Map<String, String> = emptyMap(), // periodId -> NotificationType
    val reminders: Map<String, Long> = emptyMap(), // periodId -> pre-reminder ms
    val ringtoneUri: String = "",
    val complicationSwap: Boolean = false, // swap complication title/text
) {
    val hasLocation: Boolean get() = regionId.isNotEmpty()

    fun notifTypeFor(period: Period): String =
        notifTypes[period.id] ?: period.defaultNotifType

    fun reminderFor(period: Period): Long =
        reminders[period.id] ?: period.defaultReminderMs

    fun isShown(period: Period): Boolean = when (period) {
        Period.IMSAK -> showImsak
        Period.TERBIT -> showTerbit
        Period.DHUHA -> showDhuha
        else -> true
    }
}

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("usholli_wear_preferences", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val notifTypes = mutableMapOf<String, String>()
        val reminders = mutableMapOf<String, Long>()
        for (p in Period.all) {
            notifTypes[p.id] = prefs.getString("notif_type_${p.id}", null) ?: p.defaultNotifType
            reminders[p.id] = prefs.getLong("reminder_${p.id}", p.defaultReminderMs)
        }
        return AppSettings(
            regionId = prefs.getString("region_id", "") ?: "",
            regionName = prefs.getString("region_name", "") ?: "",
            regionProv = prefs.getString("region_prov", "") ?: "",
            autoDetect = prefs.getBoolean("auto_detect", false),
            alarmMode = prefs.getBoolean("alarm_mode", false),
            showImsak = prefs.getBoolean("show_imsak", true),
            showTerbit = prefs.getBoolean("show_terbit", true),
            showDhuha = prefs.getBoolean("show_dhuha", true),
            hijriCorrection = prefs.getInt("hijri_correction", 0),
            notifTypes = notifTypes,
            reminders = reminders,
            ringtoneUri = prefs.getString("ringtone_uri", "") ?: "",
            complicationSwap = prefs.getBoolean("complication_swap", false),
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit().apply {
            putString("region_id", settings.regionId)
            putString("region_name", settings.regionName)
            putString("region_prov", settings.regionProv)
            putBoolean("auto_detect", settings.autoDetect)
            putBoolean("alarm_mode", settings.alarmMode)
            putBoolean("show_imsak", settings.showImsak)
            putBoolean("show_terbit", settings.showTerbit)
            putBoolean("show_dhuha", settings.showDhuha)
            putInt("hijri_correction", settings.hijriCorrection)
            putString("ringtone_uri", settings.ringtoneUri)
            putBoolean("complication_swap", settings.complicationSwap)
            for (p in Period.all) {
                putString("notif_type_${p.id}", settings.notifTypeFor(p))
                putLong("reminder_${p.id}", settings.reminderFor(p))
            }
        }.apply()
    }

    fun setNotifType(period: Period, type: String) {
        prefs.edit().putString("notif_type_${period.id}", type).apply()
    }

    fun setReminder(period: Period, reminderMs: Long) {
        prefs.edit().putLong("reminder_${period.id}", reminderMs).apply()
    }

    fun setRingtone(uri: String) {
        prefs.edit().putString("ringtone_uri", uri).apply()
    }

    fun setHijriCorrection(correction: Int) {
        prefs.edit().putInt("hijri_correction", correction).apply()
    }

    fun setComplicationSwap(swap: Boolean) {
        prefs.edit().putBoolean("complication_swap", swap).apply()
    }

    // --- Hijri date cache so it shows offline ---

    fun cacheHijri(date: String) {
        prefs.edit().putString("hijri_cache", date).apply()
    }

    fun loadCachedHijri(): String? = prefs.getString("hijri_cache", null)

    // --- Schedule cache (JSON) so receivers can re-schedule without re-fetching ---

    fun cacheSchedule(json: String) {
        prefs.edit().putString("schedule_cache", json).apply()
    }

    fun loadCachedSchedule(): String? = prefs.getString("schedule_cache", null)
}
