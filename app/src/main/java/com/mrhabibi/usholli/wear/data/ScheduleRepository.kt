package com.mrhabibi.usholli.wear.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mrhabibi.usholli.wear.data.remote.ApiClient
import com.mrhabibi.usholli.wear.util.NetworkUtils
import com.mrhabibi.usholli.wear.util.retryWithBackoff
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth

class ScheduleRepository(private val context: Context) {

    private val api by lazy { ApiClient.api }
    private val store = SettingsStore(context)
    private val gson = Gson()

    suspend fun listCities(): List<City> {
        if (!NetworkUtils.isOnline(context)) return emptyList()
        return try {
            api.listCities().data.orEmpty().map { City(it.id, it.lokasi) }.sortedBy { it.lokasi }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchCities(keyword: String): List<City> {
        if (!NetworkUtils.isOnline(context)) return emptyList()
        return try {
            api.searchCities(keyword).data.orEmpty().map { City(it.id, it.lokasi) }.sortedBy { it.lokasi }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun hijriToday(): String? {
        val today = LocalDate.now().toString()
        // Prefer the cached value for today (works offline across midnight).
        loadCachedHijriMap()[today]?.let { return it }

        if (!NetworkUtils.isOnline(context)) return null
        val correction = store.load().hijriCorrection
        return try {
            val date = api.hijriFor(today, adj = correction).data?.hijr?.today
            if (date != null) {
                val map = loadCachedHijriMap().toMutableMap()
                map[today] = date
                cacheHijriMap(map)
            }
            date
        } catch (e: Exception) {
            null
        }
    }

    /** Fetch and cache the Hijri date for the next [days] days (like the schedule cache). */
    suspend fun fetchAndCacheHijriRange(days: Int = 7) {
        if (!NetworkUtils.isOnline(context)) return
        val correction = store.load().hijriCorrection
        val map = loadCachedHijriMap().toMutableMap()
        val today = LocalDate.now()
        for (i in 0 until days) {
            val date = today.plusDays(i.toLong()).toString()
            if (map.containsKey(date)) continue
            runCatching {
                val hijri = api.hijriFor(date, adj = correction).data?.hijr?.today
                if (hijri != null) map[date] = hijri
            }
        }
        cacheHijriMap(map)
    }

    private val hijriMapType = object : TypeToken<Map<String, String>>() {}.type

    private fun cacheHijriMap(map: Map<String, String>) {
        store.cacheHijri(gson.toJson(map))
    }

    private fun loadCachedHijriMap(): Map<String, String> {
        val json = store.loadCachedHijri() ?: return emptyMap()
        return try {
            gson.fromJson(json, hijriMapType)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Fetch the current month + the next two months (guarantees 30+ days ahead,
     * actually ~60-90 days) and cache it. Returns null when offline or after
     * retries are exhausted.
     */
    suspend fun fetchAndCacheSchedule(regionId: String): Schedule? {
        if (!NetworkUtils.isOnline(context)) return null
        return try {
            retryWithBackoff(maxAttempts = 3) { fetchScheduleInternal(regionId) }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchScheduleInternal(regionId: String): Schedule {
        val yearMonth = YearMonth.from(LocalDate.now())
        val months = listOf(
            yearMonth,
            yearMonth.plusMonths(1),
            yearMonth.plusMonths(2),
        )

        val fetched = months.map { ym -> fetchMonth(regionId, ym.year, ym.monthValue) }
        val days = fetched
            .flatMap { it.days }
            .distinctBy { it.date }
            .sortedBy { it.date }

        if (days.isEmpty()) throw IOException("Empty schedule")

        val schedule = Schedule(regionId, fetched.first().kabko, fetched.first().prov, days)
        cacheSchedule(schedule)
        return schedule
    }

    private suspend fun fetchMonth(regionId: String, year: Int, month: Int): Schedule {
        val period = "%04d-%02d".format(year, month)
        val resp = api.schedule(regionId, period)
        if (!resp.status || resp.data == null) {
            throw IOException("Schedule API returned no data")
        }
        val days = resp.data.jadwal.orEmpty()
            .map { (date, entry) -> DaySchedule(date, entry.asMap()) }
            .sortedBy { it.date }
        return Schedule(regionId, resp.data.kabko.orEmpty(), resp.data.prov.orEmpty(), days)
    }

    fun loadCachedSchedule(): Schedule? {
        val json = store.loadCachedSchedule() ?: return null
        return try {
            gson.fromJson(json, Schedule::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun cacheSchedule(schedule: Schedule) {
        store.cacheSchedule(gson.toJson(schedule))
    }
}
