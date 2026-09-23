package com.mrhabibi.usholli.wear.data

import android.content.Context
import com.google.gson.Gson
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
        val correction = store.load().hijriCorrection
        if (!NetworkUtils.isOnline(context)) return store.loadCachedHijri()
        return try {
            val date = api.hijriToday(adj = correction).data?.hijr?.today
            if (date != null) store.cacheHijri(date)
            date
        } catch (e: Exception) {
            store.loadCachedHijri()
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
