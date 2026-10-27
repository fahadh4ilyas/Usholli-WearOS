package com.mrhabibi.usholli.wear.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.Locale

/** One-shot UI state for the "Deteksi Otomatis" location flow. */
sealed interface LocationDetection {
    data object Idle : LocationDetection
    data object Detecting : LocationDetection
    data object Success : LocationDetection
    data class Failed(val message: String) : LocationDetection
}

/** Strips the "KABUPATEN"/"KAB."/"KOTA" prefix from a Geocoder locality name. */
private val cityPrefixRegex = Regex("^(KABUPATEN|KAB\\.?|KOTA)\\s+")

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

    var detection by mutableStateOf<LocationDetection>(LocationDetection.Idle)
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

    /** Manual auto-detection: force a fresh location fix, then select the nearest city. */
    fun autoDetect() {
        detection = LocationDetection.Detecting
        detectLocation(forceFresh = true, reportToUi = true)
    }

    /**
     * Run when the app opens: re-detect the city whenever auto-detect is active,
     * or when no city has been chosen yet. Uses the last known location for speed.
     */
    fun autoDetectOnLaunch() {
        if (settings.hasLocation && !settings.autoDetect) return
        detectLocation(forceFresh = false, reportToUi = false)
    }

    fun clearDetection() {
        detection = LocationDetection.Idle
    }

    private fun detectLocation(forceFresh: Boolean, reportToUi: Boolean) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            if (!hasLocationPermission()) {
                if (reportToUi) {
                    detection = LocationDetection.Failed(app.getString(R.string.auto_detect_no_permission))
                }
                return@launch
            }
            val location = withContext(Dispatchers.IO) {
                if (forceFresh) freshLocation(app) else lastKnownLocation(app)
            }
            if (location == null) {
                if (reportToUi) {
                    detection = LocationDetection.Failed(app.getString(R.string.auto_detect_failed))
                }
                return@launch
            }
            val city = withContext(Dispatchers.IO) { reverseGeocode(app, location) }
            if (city != null) {
                applyCity(city, autoDetect = true)
                if (reportToUi) detection = LocationDetection.Success
            } else if (reportToUi) {
                detection = LocationDetection.Failed(app.getString(R.string.not_found))
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            getApplication<Application>(),
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Force a fresh location fix, preferring the fused provider (on a watch this
     * relays the paired phone's location) before the watch's own GPS/network.
     */
    private suspend fun freshLocation(app: Application): Location? {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        for (provider in locationProviders(lm)) {
            val location = withTimeoutOrNull(15_000L) { requestCurrentLocation(lm, app, provider) }
            if (location != null) return location
        }
        return lastKnownLocation(app)
    }

    /** Fused first (phone-relayed on Wear OS), then the watch's own GPS/network. */
    private fun locationProviders(lm: LocationManager): List<String> =
        listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        ).filter { it in lm.allProviders }

    private suspend fun requestCurrentLocation(
        lm: LocationManager,
        app: Application,
        provider: String,
    ): Location? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val executor = ContextCompat.getMainExecutor(app)
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            val consumer = java.util.function.Consumer<Location> { location ->
                if (location != null && cont.isActive) cont.resume(location, null)
            }
            runCatching {
                lm.getCurrentLocation(provider, signal, executor, consumer)
            }.onFailure {
                if (cont.isActive) cont.resume(null, null)
            }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (cont.isActive) cont.resume(location, null)
                }
                override fun onProviderDisabled(provider: String) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            @Suppress("DEPRECATION")
            runCatching {
                lm.requestSingleUpdate(provider, listener, null)
            }.onFailure {
                if (cont.isActive) cont.resume(null, null)
            }
        }
    }

    private fun applyCity(city: City, autoDetect: Boolean) {
        updateSettings { it.copy(regionId = city.id, regionName = city.lokasi, autoDetect = autoDetect) }
        viewModelScope.launch {
            loading = true
            error = null

            // Reuse the cached schedule for this city only if it still covers today.
            val cached = repo.loadCachedSchedule(city.id)
            val today = LocalDate.now().toString()
            if (cached != null && cached.day(today) != null) {
                schedule = cached
                updateSettings { it.copy(regionName = cached.kabko, regionProv = cached.prov) }
                AlarmScheduler.reschedule(getApplication())
                UsholliTileService.requestUpdate(getApplication())
                NextPrayerComplicationService.requestUpdate(getApplication())
                loading = false
                return@launch
            }

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
        for (provider in locationProviders(lm)) {
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
                        3,
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
                    geocoder.getFromLocation(location.latitude, location.longitude, 3)
                }.getOrNull() ?: emptyList()
            }

        val cities = repo.listCities()
        if (cities.isEmpty()) return null

        // Match each address's kabupaten/kota against the API city list.
        for (address in addresses) {
            for (name in listOf(address.subAdminArea, address.locality, address.adminArea)) {
                if (name.isNullOrBlank()) continue
                matchCity(normalizeCityName(name), cities)?.let { return it }
            }
        }
        return null
    }

    private fun normalizeCityName(raw: String): String = raw
        .uppercase(Locale("id", "ID"))
        .replace(cityPrefixRegex, "")
        .trim()

    private fun matchCity(normalized: String, cities: List<City>): City? {
        if (normalized.isEmpty()) return null
        val named = cities.map { it to normalizeCityName(it.lokasi) }
        named.firstOrNull { it.second == normalized }?.first?.let { return it }
        return named.firstOrNull { it.second.contains(normalized) }?.first
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
