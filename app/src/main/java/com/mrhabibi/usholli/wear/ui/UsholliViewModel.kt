package com.mrhabibi.usholli.wear.ui

import android.app.Application
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrhabibi.usholli.wear.R
import com.mrhabibi.usholli.wear.alarm.AlarmScheduler
import com.mrhabibi.usholli.wear.alarm.Notifications
import com.mrhabibi.usholli.wear.complication.NextPrayerComplicationService
import com.mrhabibi.usholli.wear.data.AppSettings
import com.mrhabibi.usholli.wear.data.City
import com.mrhabibi.usholli.wear.data.Period
import com.mrhabibi.usholli.wear.data.Schedule
import com.mrhabibi.usholli.wear.data.ScheduleRepository
import com.mrhabibi.usholli.wear.data.ScheduleUtil
import com.mrhabibi.usholli.wear.data.SettingsStore
import com.mrhabibi.usholli.wear.tile.UsholliTileService
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class UsholliViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SettingsStore(application)
    private val repo = ScheduleRepository(application)

    var settings by mutableStateOf(store.load())
        private set

    var schedule by mutableStateOf<Schedule?>(repo.loadCachedSchedule())
        private set

    var hijri by mutableStateOf<String?>(null)
        private set

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var cities by mutableStateOf<List<City>>(emptyList())
        private set

    var citiesLoading by mutableStateOf(false)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = null
            hijri = repo.hijriToday()
            if (settings.hasLocation) {
                val fresh = repo.fetchAndCacheSchedule(settings.regionId)
                if (fresh != null) {
                    schedule = fresh
                    if (fresh.kabko.isNotBlank()) {
                        updateSettings { it.copy(regionName = fresh.kabko, regionProv = fresh.prov) }
                    }
                    AlarmScheduler.reschedule(getApplication())
                } else {
                    schedule = repo.loadCachedSchedule()
                    if (schedule == null) {
                        error = getApplication<Application>().getString(R.string.failed_load_data)
                    } else {
                        // Offline: still (re)schedule the alarms from the cached schedule,
                        // otherwise prayer notifications silently stop after an offline launch.
                        AlarmScheduler.reschedule(getApplication())
                    }
                }
            }
            loading = false
        }
    }

    fun selectCity(city: City) = applyCity(city, autoDetect = false)

    fun setAutoDetect(enabled: Boolean) {
        updateSettings { it.copy(autoDetect = enabled) }
    }

    /** Best-effort auto-detection: reverse-geocode GPS to a locality, then match a city. */
    fun autoDetect() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val location = lastKnownLocation(app) ?: run {
                error = app.getString(R.string.location_permission_needed)
                return@launch
            }
            val city = withContext(Dispatchers.IO) { reverseGeocode(app, location) }
            if (city != null) {
                applyCity(city, autoDetect = true)
            } else {
                error = app.getString(R.string.not_found)
            }
        }
    }

    /** Run on first launch: auto-detect the location so the schedule shows immediately. */
    fun autoDetectOnLaunch() {
        if (settings.hasLocation) return
        setAutoDetect(true)
        autoDetect()
    }

    private fun applyCity(city: City, autoDetect: Boolean) {
        updateSettings { it.copy(regionId = city.id, regionName = city.lokasi, autoDetect = autoDetect) }
        viewModelScope.launch {
            loading = true
            error = null
            val fresh = repo.fetchAndCacheSchedule(city.id)
            if (fresh != null) {
                schedule = fresh
                updateSettings { it.copy(regionName = fresh.kabko, regionProv = fresh.prov) }
                AlarmScheduler.reschedule(getApplication())
                UsholliTileService.requestUpdate(getApplication())
                NextPrayerComplicationService.requestUpdate(getApplication())
            } else {
                error = getApplication<Application>().getString(R.string.failed_load_data)
            }
            loading = false
        }
    }

    private fun lastKnownLocation(app: Application): Location? {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            val location = runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
            if (location != null) return location
        }
        return null
    }

    private suspend fun reverseGeocode(app: Application, location: Location): City? {
        val geocoder = Geocoder(app, Locale("id", "ID"))
        val addresses: List<Address> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCoroutine { continuation ->
                    geocoder.getFromLocation(
                        location.latitude,
                        location.longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: List<Address>) {
                                continuation.resumeWith(Result.success(addresses))
                            }
                            override fun onError(errorMessage: String?) {
                                continuation.resumeWith(Result.success(emptyList()))
                            }
                        },
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                runCatching {
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)
                }.getOrNull() ?: emptyList()
            }

        val address = addresses.firstOrNull() ?: return null
        val locality = address.subAdminArea ?: address.locality ?: address.adminArea ?: return null
        val cleaned = locality
            .replace("Kabupaten ", "")
            .replace("Kota ", "")
            .trim()

        // Search the full list for a matching name (loaded lazily).
        return repo.listCities().firstOrNull { it.lokasi.contains(cleaned, ignoreCase = true) }
    }

    fun setAlarmMode(enabled: Boolean) {
        updateSettings { it.copy(alarmMode = enabled) }
        AlarmScheduler.reschedule(getApplication())
    }

    fun setShow(period: Period, show: Boolean) {
        val updated = when (period) {
            Period.IMSAK -> settings.copy(showImsak = show)
            Period.TERBIT -> settings.copy(showTerbit = show)
            Period.DHUHA -> settings.copy(showDhuha = show)
            else -> settings
        }
        updateSettings { updated }
        AlarmScheduler.reschedule(getApplication())
        UsholliTileService.requestUpdate(getApplication())
        NextPrayerComplicationService.requestUpdate(getApplication())
    }

    fun setNotifType(period: Period, type: String) {
        store.setNotifType(period, type)
        settings = store.load()
        AlarmScheduler.reschedule(getApplication())
    }

    fun setReminder(period: Period, reminderMs: Long) {
        store.setReminder(period, reminderMs)
        settings = store.load()
        AlarmScheduler.reschedule(getApplication())
    }

    fun setRingtone(uri: String) {
        store.setRingtone(uri)
        settings = store.load()
        AlarmScheduler.reschedule(getApplication())
    }

    fun setHijriCorrection(correction: Int) {
        store.setHijriCorrection(correction)
        settings = store.load()
        refreshHijri()
    }

    /** Re-fetch the Hijri date (called when the Gregorian date rolls over at midnight). */
    fun refreshHijri() {
        viewModelScope.launch {
            hijri = repo.hijriToday()
        }
    }

    fun setComplicationSwap(swap: Boolean) {
        store.setComplicationSwap(swap)
        settings = store.load()
        NextPrayerComplicationService.requestUpdate(getApplication())
    }

    /** Fire a sample adzan notification for the next prayer so the user can verify it. */
    fun simulateNotification() {
        val cached = schedule ?: repo.loadCachedSchedule()
        val next = cached?.let { ScheduleUtil.nextPrayer(it, settings) }
        val period = next?.period ?: Period.DZUHUR
        Notifications.showAdzan(getApplication(), period.id)
    }

    fun loadCities() {
        if (cities.isNotEmpty()) return
        viewModelScope.launch {
            citiesLoading = true
            cities = repo.listCities()
            citiesLoading = false
        }
    }

    fun searchCities(keyword: String) {
        viewModelScope.launch {
            citiesLoading = true
            cities = if (keyword.isBlank()) repo.listCities() else repo.searchCities(keyword)
            citiesLoading = false
        }
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings)
        store.save(settings)
    }
}
