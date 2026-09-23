package com.mrhabibi.usholli.wear.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** myquran v3 API — see apimuslim.json. */
interface MyQuranApi {

    @GET("v3/sholat/kabkota/semua")
    suspend fun listCities(): LocationResponse

    @GET("v3/sholat/kabkota/cari/{keyword}")
    suspend fun searchCities(@Path("keyword") keyword: String): LocationResponse

    @GET("v3/sholat/jadwal/{id}/today")
    suspend fun scheduleToday(
        @Path("id") id: String,
        @Query("tz") tz: String = "Asia/Jakarta",
    ): JadwalResponse

    @GET("v3/sholat/jadwal/{id}/{period}")
    suspend fun schedule(
        @Path("id") id: String,
        @Path("period") period: String, // YYYY-MM or YYYY-MM-DD
    ): JadwalResponse

    @GET("v3/cal/today")
    suspend fun hijriToday(
        @Query("tz") tz: String = "Asia/Jakarta",
        @Query("method") method: String = "islamic-umalqura",
        @Query("adj") adj: Int = 0,
    ): CalendarResponse
}

// --- DTOs ---

data class LocationResponse(
    val status: Boolean,
    val message: String?,
    val data: List<CityDto>?,
)

data class CityDto(
    val id: String,
    val lokasi: String,
)

data class JadwalResponse(
    val status: Boolean,
    val message: String?,
    val data: JadwalData?,
)

data class JadwalData(
    val id: String?,
    val kabko: String?,
    val prov: String?,
    val jadwal: Map<String, JadwalEntry>?,
)

data class JadwalEntry(
    val tanggal: String?,
    val imsak: String?,
    @SerializedName("subuh") val subuh: String?,
    val terbit: String?,
    val dhuha: String?,
    val dzuhur: String?,
    val ashar: String?,
    val maghrib: String?,
    val isya: String?,
) {
    fun asMap(): Map<String, String> = buildMap {
        imsak?.let { put("imsak", it) }
        subuh?.let { put("subuh", it) }
        terbit?.let { put("terbit", it) }
        dhuha?.let { put("dhuha", it) }
        dzuhur?.let { put("dzuhur", it) }
        ashar?.let { put("ashar", it) }
        maghrib?.let { put("maghrib", it) }
        isya?.let { put("isya", it) }
    }
}

data class CalendarResponse(
    val status: Boolean,
    val message: String?,
    val data: CalendarData?,
)

data class CalendarData(
    val method: String?,
    val adjustment: Int?,
    val ce: CalendarDateInfo?,
    val hijr: CalendarDateInfo?,
)

data class CalendarDateInfo(
    val today: String?,
    val day: Int?,
    val dayName: String?,
    val month: Int?,
    val monthName: String?,
    val year: Int?,
)
