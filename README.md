# Salute Sync

Android app that reads the user's data from **Health Connect** (where Samsung Health can sync it) and sends it as JSON to a webhook once a day.

There is no backend, no analytics, and no third-party trackers. The URL and the key stay on the phone, encrypted with `EncryptedSharedPreferences` (AES-256, key in the Android Keystore).

The phone UI defaults to Italian and can be switched to English in Settings. Dates on screen use DD-MM-YYYY. Webhook JSON, stored dates, and this README stay in English, and payload dates stay ISO `yyyy-MM-dd`.

- Kotlin, Jetpack Compose
- `minSdk` 28, `compileSdk` / `targetSdk` 37 (Android 17)
- Health Connect `connect-client` 1.1.0
- WorkManager, sync around 06:00 in the phone's time zone

## Install on a phone

The sideload APK is signed (see below). Version 1.3.3 is `salute-sync-1.3.3.apk`. Webhook URL, header, and key are on the Settings screen. The main screen shows sync status and a short notice if the webhook or Health Connect permissions are missing.

1. Copy the APK to the phone (Drive, email, cable, or download it in the browser).
2. Open it. If Android blocks the install, allow **Install unknown apps** for the app you are opening it from (Chrome, Files, Gmail, and so on). On Android 8 and later this permission is per app, not a global switch: Settings → Apps → the app used to open the file → Install unknown apps.
3. Confirm the install and open **Salute Sync**.
4. On Android 13 or earlier, if Health Connect is missing, tap **Installa o aggiorna Health Connect** (Play Store, package `com.google.android.apps.healthdata`). On Android 14 and later Health Connect is part of the system: Settings → Security and privacy → Privacy controls → Health Connect, or search for "Health Connect".
5. In **Samsung Health**, turn on sync to Health Connect for the types you want (steps, sleep, weight, workouts, heart rate, and the others). The menu path changes with the Samsung Health version: search for "Health Connect" in Samsung Health settings, then check in Health Connect that Samsung Health is listed as a source.
6. In Salute Sync tap **Concedi permessi di lettura** and accept the types you want to send. Denied types are not read and are listed in `unavailableMetrics`. They are not treated as zeros.
7. If the phone supports it, tap **Consenti lettura in background**. Without that permission the 06:00 sync cannot read data while the screen is off. **Sincronizza ora** still works while the app is open.
8. Paste the webhook URL (`https://` is recommended; `http://` is allowed, for example on a local network) and the header value. The header name defaults to `Authorization` and can be changed.
9. Tap **Salva impostazioni**, then **Sincronizza ora**.
10. Check the time and result of the last sync at the bottom. The automatic sync is a WorkManager job every 24 hours, with the first run aimed at 06:00 in the phone's time zone (in Italy, `Europe/Rome`). Android may delay it while battery saving is on. The **Non ottimizzare la batteria** button opens the system request.

To update the app, install a newer APK signed with the same key over this one. Uninstalling clears the URL, the key, and the time of the last sync.

## What is sent

`POST` to the saved URL.

- `Content-Type: application/json; charset=utf-8`
- The header name and value you configured (default name `Authorization`)
- `User-Agent: SaluteSync/1.3.3`

Every POST body is at most **3,500 bytes**. Requests are sent **one at a time**, with at least **60 seconds** between them (the pause can be raised in the app, not lowered). WorkManager keeps the queue going in the background. Progress is stored on the phone, so a failure resumes at the next unsent day.

The first sync imports history. By default it starts at the earliest day that has something other than calorie estimates, and it does not POST days that contain only calories. You can set an explicit start date (`yyyy-MM-dd`). **Reimporta storico** clears the cursor and starts again. Later mornings send only the new days (`k` is `i` instead of `b`).

With the history permission the search can go back 15 years. Without it, Health Connect only allows data from 30 days before the first read grant, and the Italian screen says so.

Steps, sleep, weight, and workouts are requested and read. The screen shows the record counts from the last read. A read that contains only calories is not treated as a permission error: there may simply be no other data. Denied Health Connect read permissions are still listed in Settings.

Daily totals (steps, distance, calories, heart rate, resting heart rate, hydration) come from Health Connect aggregation, and only for the slice of the day inside the window. Steps written by more than one source are not added twice. Heart-rate variability, oxygen, and nutrition are computed from the samples that were read. Sleep, weight, body fat, and workouts stay as individual events.

A sleep session or a workout is included when its **start** falls inside the window. Duration and stages are the full session, even if the end is past the window. Workout calories and distance are the Health Connect aggregate over the session interval.

If Health Connect rejects a window that starts more than 30 days before the permission was granted, the app retries the last 30 days and sets `"historyLimited": true`. The extra history permission is not requested.

On a network error, or HTTP 408, 429, or 5xx, WorkManager retries with exponential backoff (minimum 15 minutes, up to 5 attempts in that cycle). A wrong URL or key (other 4xx) is not retried in a tight loop. The periodic sync stays scheduled either way.

## JSON schema (version 3)

Null fields are omitted. Numbers are integers except weights, temperatures, glucose, respiratory rate, and VO2, which keep one or two decimals. Each body is UTF-8 and at most 3500 bytes. `k` is `b` for history and `i` for the daily sync. `i` is the 1-based number of this POST in the current run.

```json
{"v":3,"k":"b","i":1,"z":"Europe/Rome","app":"1.2.0","t":"2026-11-18T16:00:00+01:00","a":"2024-06-01","b":"2024-06-01","d":[{"d":"2024-06-01","st":8432,"dm":6120,"ak":320,"tk":2100,"hr":[52,72,145],"rh":58,"sl":[{"m":480,"s":"06-01 23:00","e":"06-02 07:00","g":{"d":90,"l":220,"r":80}}],"wt":74.2,"bf":18.5,"ex":[{"y":"run","m":45,"k":420}]}]}
```

| Key | Meaning |
| --- | --- |
| `v` | Schema version, `3`. |
| `k` | `b` backfill, `i` incremental. |
| `i` | 1-based POST index in this run. |
| `z` | Phone time zone id. |
| `app` | App version name. |
| `t` | When this body was built. |
| `a`, `b` | First and last local dates in `d`. |
| `d[].d` | Local date. |
| `st` | Steps. Omitted when zero or missing. |
| `dm` | Distance, meters. |
| `ak`, `tk`, `bk` | Active, total, and basal kilocalories. |
| `hr` | `[min, avg, max]` bpm. |
| `rh` | Resting heart rate. |
| `hv` | HRV RMSSD milliseconds, `[min, avg, max]`. |
| `o2` | Oxygen saturation percent, `[min, avg, max]`. |
| `sl` | Sleep sessions: `m` minutes, `s`/`e` local `MM-dd HH:mm`, `g` stage minutes (`d` deep, `l` light, `r` REM, `a` awake, `s` sleeping, `o` out of bed, `i` awake in bed, `u` unknown). |
| `wt` | Last weight, kg. |
| `bf` | Last body-fat percent. |
| `ex` | Workouts: `y` short type (`run`, `walk`, `bike`, `strength`, `hiit`, `swim`, …), `m` minutes, `k` active kcal. |
| `fl`, `el`, `wp` | Floors, elevation meters, wheelchair pushes. |
| `sp`, `pw`, `cc`, `sc` | Speed m/s, power watts, cycling rpm, step cadence. Each is `[min, avg, max]`. |
| `hy` | Hydration, milliliters. |
| `ht` | Last height, centimeters. |
| `lm`, `bn`, `wa` | Last lean mass, bone mass, body water, kg. |
| `bt`, `bb` | Last body temperature and basal temperature, Celsius. |
| `bp` | Last blood pressure, `[systolic, diastolic]` mmHg. |
| `bg` | Last glucose, mmol/L. |
| `rr` | Last respiratory rate. |
| `vo` | Last VO2 max, ml/min/kg. |
| `nu` | Nutrition for the day, `[kcal, protein g, carbs g, fat g]`. |

Days that contain only `ak`, `tk`, and `bk` are not sent during backfill.

## APK signing

The `release` build is signed with the keystore in the repository, `keystore/salute-sync.jks` (alias `salutesync`, store password and key password `salutesync`). This is a sideload key, public on purpose, not a Play Store key. It is fine for installing and updating this app outside the store. Do not use it to publish on Google Play.

`EncryptedSharedPreferences` is the API used for the URL and the key. Jetpack marked it deprecated in `security-crypto` 1.1.0 and has not shipped an official replacement. It stays in use here, and the preferences file is recreated if the keyset cannot be read.

## Build locally

You need JDK 17 or newer and Android SDK 37 (platform `android-37` / `android-37.0` and build-tools 37).

```bash
export ANDROID_HOME="$HOME/android-sdk"
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

The APK is at `app/build/outputs/apk/release/salute-sync-release.apk`.

Unit tests cover JSON construction (daily totals, sleep with stages, weight, workouts, `Europe/Rome`), the 7-day window and later syncs, the 06:00 schedule, and URL and header validation.
