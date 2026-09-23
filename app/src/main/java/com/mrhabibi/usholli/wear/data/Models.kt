package com.mrhabibi.usholli.wear.data

/** A kabupaten/kota returned by the myquran v3 API. */
data class City(
    val id: String,
    val lokasi: String,
)

/** Prayer times for a single day, keyed by API field name (subuh, terbit, ...). */
data class DaySchedule(
    val date: String, // yyyy-MM-dd
    val times: Map<String, String>, // apiField -> "HH:mm"
) {
    fun timeOf(period: Period): String? = times[period.apiField]
}

/** A parsed month of prayer times for one location. */
data class Schedule(
    val cityId: String,
    val kabko: String,
    val prov: String,
    val days: List<DaySchedule>, // sorted ascending by date
) {
    fun day(date: String): DaySchedule? = days.firstOrNull { it.date == date }
}

data class HijriDate(
    val today: String, // e.g. "Selasa, 11 Rabiulakhir 1448 H"
)
