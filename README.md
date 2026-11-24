# Salute Sync

Android app that reads the user's data from **Health Connect** (where Samsung Health can sync it) and sends it as JSON to a webhook once a day.

There is no backend, no analytics, and no third-party trackers. The URL and the key stay on the phone, encrypted with `EncryptedSharedPreferences` (AES-256, key in the Android Keystore).

The phone UI defaults to Italian and can be switched to English in Settings, and again on the first-launch screen. That choice is the Android per-app language, so on Android 13 and later it matches Settings > Apps > Salute Sync > Language. Dates on screen use DD-MM-YYYY (and DD-MM-YYYY HH:mm when a time is shown). Webhook JSON and stored dates stay ISO `yyyy-MM-dd`. This README is English.

- Kotlin, Jetpack Compose
- `minSdk` 28, `compileSdk` 37.1, `targetSdk` 37
- Health Connect `connect-client` 1.1.0
- WorkManager, sync around 06:00 in the phone's time zone

## Install on a phone

The sideload APK is signed (see below). The current release asset is `salute-sync-2.0.0.apk`, published on the GitHub release `v2.0.0` (`https://github.com/scardracs/health-connect-sync/releases/download/v2.0.0/salute-sync-2.0.0.apk`). Older tags use the same pattern: `salute-sync-<version>.apk`.

The main screen shows sync status, last sync, per-type counts, **Sync now**, and **Reimport history**. If the webhook or Health Connect read permissions are missing, a short notice links to Settings. Webhook URL, header name, header value (masked until shown), language, morning sync, and Health Connect permissions are on the Settings screen. The first time the app opens, including after an update from an older version, a dialog asks for the language and offers the Health Connect setup. **Skip** closes it and does not turn off the morning sync. It does not appear again.

1. Copy the APK to the phone (Drive, email, cable, or a browser download).
2. Open it. If Android blocks the install, allow **Install unknown apps** for the app you are opening it from (Chrome, Files, Gmail, and so on). On Android 8 and later this permission is per app, not a global switch: Settings → Apps → the app used to open the file → Install unknown apps.
3. Confirm the install and open **Salute Sync**.
4. In the welcome dialog, pick **Italiano** or **English**. The rest of the app switches immediately.
5. Still in the dialog, or later in Settings, tap **Grant read permissions** and accept the types you want to send. **Open Health Connect** uses the system permission screen when the phone has one, then Health Connect settings, then the Play Store page, then this app's system details. Denied types are not read. Settings lists each type as granted or not granted.
6. On Android 13 or earlier, if Health Connect is missing, Settings offers **Install or update Health Connect** (Play Store, package `com.google.android.apps.healthdata`). On Android 14 and later Health Connect is part of the system: Settings → Security and privacy → Privacy controls → Health Connect, or search for "Health Connect".
7. In **Samsung Health**, turn on sync to Health Connect for the types you want (steps, sleep, weight, workouts, heart rate, and the others). The menu path changes with the Samsung Health version: search for "Health Connect" in Samsung Health settings, then check in Health Connect that Samsung Health is listed as a source.
8. If the phone supports it, in Settings tap **Allow background read** and **Allow history read**. Without background read, the 06:00 sync cannot read while the screen is off. **Sync now** still works with the app open. Without history read, Health Connect only allows data from 30 days before the first permission grant. The screen says so.
9. In Settings, paste the webhook URL (`https://` is recommended; `http://` is allowed, for example on a local network) and the header value. The header name defaults to `Authorization` and can be changed. The key stays masked until you tap **Show key**.
10. Tap **Save settings**, then on the main screen **Sync now**.
11. The main screen shows the last success and the last attempt as DD-MM-YYYY HH:mm. In Settings, **Send this range** resends the days between **From** and **To** without a full history import. **Wi-Fi only** and **Only while charging** apply to the automatic sync. **Automatic sync every** sets the interval in hours (default 24, which still aims at 06:00 in the phone's time zone). Leave **Sync every morning around 06:00** on if you want that job. Android may delay it while battery saving is on. **Don't optimize battery** on the main screen opens the system request.

To update the app, install a newer APK signed with the same key over this one. Uninstalling clears the URL, the key, and the time of the last sync.

## What is sent

`POST` to the saved URL.

- `Content-Type: application/json; charset=utf-8`
- The header name and value you configured (default name `Authorization`)
- `User-Agent: SaluteSync/2.0.0`

Every POST body is at most **3,500 bytes**. The app reads the whole range first, then sends the chunks **one at a time**. The pause defaults to **60 seconds** and can be set down to 10; Settings warns that a shorter pause can be dropped by the receiver. WorkManager keeps the queue going in the background, including a Wi-Fi-only and charging-only option for the automatic run. The automatic run defaults to every 24 hours, aimed at 06:00, and can be set from 1 to 168 hours. A failure resumes at the next unsent chunk of the same batch. After three failed attempts in a row the phone shows a notification. One failure does not.

The first sync imports history. By default it starts at the earliest day that has something other than calorie estimates, and it does not POST days that contain only calories. You can set an explicit start date on the Settings screen; the field is shown as DD-MM-YYYY and stored as `yyyy-MM-dd`. **Reimport history** clears the cursor and starts again. Later mornings send only the new days (`k` is `i` instead of `b`).

With the history permission the search can go back 15 years. Without it, Health Connect only allows data from 30 days before the first read grant, and the screen says so. Settings can request that permission.

Steps, sleep, weight, and workouts are requested and read. The screen shows the record counts from the last read. A read that contains only calories is not treated as a permission error: there may simply be no other data. Denied Health Connect read permissions are still listed in Settings.

Daily totals (steps, distance, calories, heart rate, resting heart rate, hydration) come from Health Connect aggregation, and only for the slice of the day inside the window. Steps written by more than one source are not added twice. Heart-rate variability, oxygen, and nutrition are computed from the samples that were read. Sleep, weight, body fat, and workouts stay as individual events.

A sleep session or a workout is included when its **start** falls inside the window. Duration and stages are the full session, even if the end is past the window. Workout calories and distance are the Health Connect aggregate over the session interval.

If Health Connect rejects a window that starts more than 30 days before the permission was granted, the app retries the last 30 days. The compact webhook body does not add an extra field for that case. The status text on the phone explains when the import stops inside that window.

On a network error, or HTTP 408, 429, or 5xx, WorkManager retries with exponential backoff (minimum 15 minutes, up to 5 attempts in that cycle). A wrong URL or key (other 4xx) is not retried in a tight loop. The periodic sync stays scheduled either way.

## JSON schema (version 4)

Null fields are omitted. Numbers are integers except weights, temperatures, glucose, respiratory rate, and VO2, which keep one or two decimals. Each body is UTF-8 and at most 3500 bytes. Dates inside the JSON stay ISO `yyyy-MM-dd`. The app collects every day for the operation first, then sends the chunks. `k` is `b` for history, `i` for the scheduled or manual incremental sync, and `r` for a date range chosen in Settings.

`bid` is the same on every send of one operation. `i` is the 1-based index, `n` is how many sends are in the batch, and `f` is `1` only on the last send (`0` on the others). A sync that fits in one POST has `i` 1, `n` 1, and `f` 1. The receiver can store earlier sends and analyse the batch when `f` is 1.

Middle send (`i` 2 of 4):

```json
{"v":4,"k":"b","bid":"0192ab34cd56","i":2,"n":4,"f":0,"z":"Europe/Rome","app":"2.0.0","t":"2026-11-24T06:00:00+01:00","a":"2024-01-08","b":"2024-01-14","d":[{"d":"2024-01-08","st":8100}]}
```

Final send of the same batch:

```json
{"v":4,"k":"b","bid":"0192ab34cd56","i":4,"n":4,"f":1,"z":"Europe/Rome","app":"2.0.0","t":"2026-11-24T06:00:00+01:00","a":"2024-01-22","b":"2024-01-28","d":[{"d":"2024-01-22","st":7900}]}
```

One day, one POST:

```json
{"v":4,"k":"i","bid":"0192ab34cd56","i":1,"n":1,"f":1,"z":"Europe/Rome","app":"2.0.0","t":"2026-11-18T16:00:00+01:00","a":"2024-06-01","b":"2024-06-01","d":[{"d":"2024-06-01","st":8432,"dm":6120,"ak":320,"tk":2100,"hr":[52,72,145],"rh":58,"sl":[{"m":480,"s":"06-01 23:00","e":"06-02 07:00","g":{"d":90,"l":220,"r":80}}],"wt":74.2,"bf":18.5,"ex":[{"y":"run","m":45,"k":420}]}]}
```

| Key | Meaning |
| --- | --- |
| `v` | Schema version, `4`. |
| `k` | `b` backfill, `i` incremental, `r` date range. |
| `bid` | Batch id, identical on every send of this operation. |
| `i` | 1-based index of this POST inside the batch. |
| `n` | Total POSTs in the batch. |
| `f` | `1` on the last POST, `0` on every earlier POST. |
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

Local release builds are signed with the keystore in the repository, `keystore/salute-sync.jks` (alias `salutesync`, store password and key password `salutesync`). This is a sideload key, public on purpose, not a Play Store key. It is fine for installing and updating this app outside the store. Do not use it to publish on Google Play.

GitHub releases are built by `.github/workflows/release.yml` when a `v*` tag is pushed. That job does not read the keystore file from the repository. It decodes the keystore from Actions secrets and signs with those values. The secret names are listed in the workflow.

`EncryptedSharedPreferences` is the API used for the URL and the key. Jetpack marked it deprecated in `security-crypto` 1.1.0 and has not shipped an official replacement. It stays in use here, and the preferences file is recreated if the keyset cannot be read.

## Build locally

You need JDK 17 or newer and Android SDK platform `android-37.1` (and build-tools 37). `targetSdk` stays 37.

```bash
export ANDROID_HOME="$HOME/android-sdk"
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

The APK is at `app/build/outputs/apk/release/salute-sync-release.apk`.

Unit tests cover JSON construction (daily totals, sleep with stages, weight, workouts, `Europe/Rome`), the 7-day window and later syncs, the 06:00 schedule, and URL and header validation.
