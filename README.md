# Usholli Wear OS

A Wear OS port of **Usholli** (the Indonesian Ministry of Religious Affairs / Kemenag
prayer-times app), built with **Jetpack Compose for Wear OS**.

## Features

- **Schedule** — a home screen with a time-of-day sky gradient, location, date
  (Gregorian + Hijri), a countdown to the next prayer, and the full daily prayer list.
- **Per-prayer notification preference** (bell button on each row) with 6 types:
  Tidak Ada, Sunyi, Getar, Bawaan Sistem, Ringtone, Takbir. (The full "Adzan" type is
  removed.) Includes a pre-reminder setting (5–30 minutes).
- **Qibla compass** — sensor-based (accelerometer + magnetometer) with GPS, a blue
  line to the Kaaba when the watch is facing it, and a vibration when aligned.
- **Settings** — pick a city, auto-detect location, alarm mode, show/hide
  Imsak / Terbit / Dhuha, Hijri date correction, and a complication title/text swap.
- **Notifications & alarms** — scheduled via `AlarmManager`, rescheduled after each
  prayer time, and restored on boot. Exact-alarm permission is requested on first
  launch.
- **Tiles** — a full-screen tile showing the whole day's schedule, plus Samsung
  One UI Watch compact card tiles (2×2 full schedule and 2×1 city + next prayer).
- **Complication** — a watch-face complication showing the next prayer name and time.

The **Masjid** menu is intentionally removed.

## Data source

Uses the [API Muslim v3](https://api.myquran.com/v3/doc):

- `GET /sholat/kabkota/semua` and `/sholat/kabkota/cari/{keyword}` — city list.
- `GET /sholat/jadwal/{id}/{YYYY-MM}` — monthly schedule.
- `GET /cal/today?tz=Asia/Jakarta&method=islamic-umalqura` — Hijri date.
- Qibla direction is computed locally (Kaaba `21.4225, 39.8261`).

Schedules are cached offline ahead of time, and the takbir sound
(`res/raw/takbir.mp3`) is bundled from the original APK.

## Build

Open this folder in Android Studio, or run `./gradlew assembleDebug` from the CLI.
Requires JDK 17. Uses `compileSdk 35` and `minSdk 26`.

## Structure

```
app/src/main/java/com/mrhabibi/usholli/wear/
├── alarm/        AlarmScheduler, NotificationReceiver, BootReceiver, Notifications
├── data/         Period, Models, SettingsStore, ScheduleRepository, ScheduleUtil, remote/
├── tile/         UsholliTileService (full + Samsung 2x2/2x1 compact cards)
├── complication/ NextPrayerComplicationService (watch-face complication)
├── ui/           home, kiblat, settings, location, preference, UsholliViewModel, UsholliApp
└── util/         Qibla
```
