# Usholli Wear OS

Versi Wear OS dari aplikasi **Usholli** (jadwal sholat Kemenag RI). Dibangun dengan
**Jetpack Compose for Wear OS**.

## Fitur

- **Jadwal** — tampilan home dengan sky gradient, lokasi, tanggal (Masehi + Hijriah),
  hitung mundur waktu sholat berikutnya, dan daftar waktu sholat.
- **Preferensi notifikasi** per waktu sholat (tombol lonceng di tiap baris) dengan 6 tipe:
  Tidak Ada, Sunyi, Getar, Bawaan Sistem, Ringtone, Takbir. (Tipe "Adzan" penuh dihapus.)
  Termasuk pengaturan pengingat sebelumnya (5–30 menit).
- **Kiblat** — kompas arah kiblat berbasis sensor (akselerometer + magnetometer) dan GPS.
- **Pengaturan** — pilih kota, deteksi otomatis, mode alarm, dan tampilkan/sembunyikan
  Imsak / Terbit / Dhuha.
- **Notifikasi & alarm** — dijadwalkan lewat `AlarmManager`, ditampilkan ulang setelah
  waktu sholat, dan dijadwalkan ulang saat boot.
- **Tile (widget besar)** — kartu Wear OS (swipe dari watch face) yang menampilkan
  seluruh jadwal hari ini, dengan waktu sholat berikutnya ditandai.
- **Complication (widget kecil)** — data watch face yang menampilkan nama + jam sholat
  berikutnya beserta hitung mundur (auto-update).

Menu **Masjid** sengaja dihapus.

## Sumber data

Menggunakan [API Muslim v3](https://api.myquran.com/v3/doc) (lihat `apimuslim.json`):

- `GET /sholat/kabkota/semua` dan `/sholat/kabkota/cari/{keyword}` — daftar kota.
- `GET /sholat/jadwal/{id}/{YYYY-MM}` — jadwal bulanan.
- `GET /cal/today?tz=Asia/Jakarta&method=islamic-umalqura` — tanggal Hijriah.
- Arah kiblat dihitung lokal (Kaaba `21.4225, 39.8261`).

Suara takbir (`res/raw/takbir.mp3`) dibundel dari APK asli.

## Build

Buka folder ini di Android Studio (atau jalankan `./gradlew assembleDebug` dari CLI).
Membutuhkan JDK 17. SDK yang digunakan: `compileSdk 35`, `minSdk 26`.

## Struktur

```
app/src/main/java/com/mrhabibi/usholli/wear/
├── alarm/     AlarmScheduler, NotificationReceiver, BootReceiver, Notifications
├── data/      Period, Models, SettingsStore, ScheduleRepository, ScheduleUtil, remote/
├── tile/      UsholliTileService (large widget)
├── complication/  NextPrayerComplicationService (small widget)
├── ui/        home, kiblat, settings, location, preference, UsholliViewModel, UsholliApp
└── util/      Qibla
```
