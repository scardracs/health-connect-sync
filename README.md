# Salute Sync

Android app that reads the user's data from **Health Connect** (where Samsung Health can sync it) and sends it as JSON to a webhook once a day.

There is no backend, no analytics, and no third-party trackers. The URL and the key stay on the phone, encrypted with `EncryptedSharedPreferences` (AES-256, key in the Android Keystore).

The in-app UI is Italian. This README, the code comments, and the release notes are English.

- Kotlin, Jetpack Compose
- `minSdk` 28, `compileSdk` / `targetSdk` 37 (Android 17)
- Health Connect `connect-client` 1.1.0
- WorkManager, sync around 06:00 in the phone's time zone

## Install on a phone

The sideload APK is signed (see below) and is named `salute-sync-1.0.0.apk`.

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
- `User-Agent: SaluteSync/1.1.0`

The first sync, and the **Importa storico completo** button, import every readable record from the earliest available instant to now. With the history permission that start is 15 years ago. Without it, Health Connect only allows data from 30 days before the app was first granted read permission, and the screen says so in Italian. Later daily syncs start at the end of the last successful import. The range is `[start, end)`: a record that falls exactly on the end is included in the next sync, so it is neither duplicated nor dropped.

History is posted as monthly chunks (`kind` is `backfill`). Each chunk carries `chunk.index`, `chunk.total`, and the chunk `start` / `end`. Progress is stored on the phone, so a failed chunk is retried without resending the ones that already succeeded. A chunk larger than about 1.5 MB is split into weeks, then days, before it is sent. Empty months are still posted so the index stays contiguous. After the import finishes, the daily job sends `kind` `incremental`.

Daily totals (steps, distance, calories, heart rate, resting heart rate, hydration) come from Health Connect aggregation, and only for the slice of the day inside the window. Steps written by more than one source are not added twice. Heart-rate variability, oxygen, and nutrition are computed from the samples that were read. Sleep, weight, body fat, and workouts stay as individual events.

A sleep session or a workout is included when its **start** falls inside the window. Duration and stages are the full session, even if the end is past the window. Workout calories and distance are the Health Connect aggregate over the session interval.

If Health Connect rejects a window that starts more than 30 days before the permission was granted, the app retries the last 30 days and sets `"historyLimited": true`. The extra history permission is not requested.

On a network error, or HTTP 408, 429, or 5xx, WorkManager retries with exponential backoff (minimum 15 minutes, up to 5 attempts in that cycle). A wrong URL or key (other 4xx) is not retried in a tight loop. The periodic sync stays scheduled either way.

## JSON schema (version 2)

Null fields are omitted. Decimal numbers are rounded to 2 digits. Instants are ISO-8601 with the phone time-zone offset. `unavailableMetrics` lists ids that were not granted, in alphabetical order. `kind` is `incremental` or `backfill`. `chunk` is present only for `backfill`.

```json
{
  "schemaVersion": 2,
  "kind": "incremental",
  "syncedAt": "2026-01-17T06:00:00+01:00",
  "timezone": "Europe/Rome",
  "appVersion": "1.0.0",
  "historyLimited": false,
  "window": {
    "start": "2026-01-16T06:00:00+01:00",
    "end": "2026-01-17T06:00:00+01:00"
  },
  "days": [
    {
      "date": "2026-01-16",
      "steps": 8432,
      "distanceMeters": 6120.5,
      "activeCaloriesKcal": 320.4,
      "totalCaloriesKcal": 2100.0,
      "heartRate": { "min": 52, "avg": 72, "max": 145, "sampleCount": 120 },
      "restingHeartRateBpm": 58,
      "heartRateVariabilityRmssdMillis": { "min": 20.0, "avg": 42.5, "max": 65.0, "sampleCount": 2 },
      "oxygenSaturationPercent": { "min": 97.0, "avg": 98.0, "max": 99.0, "sampleCount": 2 },
      "nutrition": {
        "energyKcal": 500.0,
        "proteinGrams": 30.0,
        "carbohydrateGrams": 40.0,
        "fatGrams": 10.0,
        "mealCount": 2
      },
      "hydrationMilliliters": 1800.0
    }
  ],
  "sleepSessions": [
    {
      "start": "2026-01-16T23:00:00+01:00",
      "end": "2026-01-17T07:00:00+01:00",
      "durationMinutes": 480,
      "notes": "casa",
      "stages": [
        {
          "stage": "deep",
          "start": "2026-01-16T23:30:00+01:00",
          "end": "2026-01-17T01:00:00+01:00",
          "durationMinutes": 90
        },
        {
          "stage": "rem",
          "start": "2026-01-17T05:00:00+01:00",
          "end": "2026-01-17T06:00:00+01:00",
          "durationMinutes": 60
        }
      ]
    }
  ],
  "weightReadings": [
    { "time": "2026-01-16T08:00:00+01:00", "weightKg": 74.2 }
  ],
  "bodyFatReadings": [
    { "time": "2026-01-16T08:00:00+01:00", "percentage": 18.5 }
  ],
  "exerciseSessions": [
    {
      "start": "2026-01-16T18:00:00+01:00",
      "end": "2026-01-16T18:45:00+01:00",
      "durationMinutes": 45,
      "exerciseType": "running",
      "title": "Parco",
      "activeCaloriesKcal": 420.0,
      "distanceMeters": 7000.0
    }
  ],
  "unavailableMetrics": []
}
```

### Fields

| Field | Meaning |
| --- | --- |
| `schemaVersion` | `2` in this app. |
| `kind` | `incremental` for the daily sync, `backfill` for a history chunk. |
| `chunk` | For `backfill`: `index` (1-based), `total`, `start`, `end`. The end matches `window.end`. |
| `syncedAt` | Instant when the app built the JSON. |
| `timezone` | The phone's `ZoneId`, for example `Europe/Rome`. |
| `appVersion` | The app `versionName`. |
| `historyLimited` | `true` when the window was shortened to Health Connect's 30-day limit. |
| `window.start` / `window.end` | Range that was read. The end is exclusive. |
| `days[]` | One object per local day that has at least one value. Empty days are omitted. `steps: 0` is kept, because zero is a real total. |
| `heartRate` | Minimum, average, and maximum bpm, plus the sample count, from the Health Connect aggregate. |
| `restingHeartRateBpm` | Average of that day's resting readings (`BPM_AVG`). |
| `heartRateVariabilityRmssdMillis` | Min / average / max RMSSD, in milliseconds. |
| `oxygenSaturationPercent` | Min / average / max saturation, as a percentage (not a fraction). |
| `nutrition` | Sum of energy, protein, carbohydrate, and fat for meals that started that day. `mealCount` also counts meals that omit those fields. |
| `hydrationMilliliters` | Total volume in the part of the day inside the window. |
| `floorsClimbed` | Floors climbed in that slice of the day. |
| `elevationMeters` | Elevation gained, in meters. |
| `wheelchairPushes` | Wheelchair push count. |
| `speedMetersPerSecond` | Min / average / max speed. `sampleCount` is `0` because the aggregate does not report a count. |
| `powerWatts` | Min / average / max power, in watts. `sampleCount` is `0`. |
| `cyclingCadenceRpm` | Min / average / max pedaling cadence. Read with the exercise permission. |
| `stepsCadencePerMinute` | Min / average / max step cadence. Read with the steps permission. |
| `basalCaloriesKcal` | Basal calories burned that day. |
| `heightReadings` | `{ time, value }` in meters. |
| `leanBodyMassReadings`, `boneMassReadings`, `bodyWaterMassReadings` | `{ time, value }` in kilograms. |
| `respiratoryRateReadings` | Breaths per minute. |
| `basalMetabolicRateReadings` | Kilocalories per day at the sample time. |
| `bodyTemperatureReadings`, `basalBodyTemperatureReadings` | `{ time, celsius, location }`. `location` is `armpit`, `finger`, `forehead`, `mouth`, `rectum`, `temporal_artery`, `toe`, `ear`, `wrist`, `vagina`, or `unknown`. |
| `bloodPressureReadings` | `{ time, systolicMmHg, diastolicMmHg, bodyPosition, measurementLocation }`. Position: `standing`, `sitting`, `lying`, `reclining`, `unknown`. Location: `left_wrist`, `right_wrist`, `left_upper_arm`, `right_upper_arm`, `unknown`. |
| `bloodGlucoseReadings` | `{ time, millimolesPerLiter, specimenSource, relationToMeal }`. |
| `vo2MaxReadings` | `{ time, millilitersPerMinutePerKg, method }` where method is `metabolic_cart`, `heart_rate_ratio`, `cooper_test`, `multistage_fitness_test`, `rockport_fitness_test`, or `other`. |
| `sleepSessions[].stages[].stage` | `awake`, `sleeping`, `out_of_bed`, `light`, `deep`, `rem`, `awake_in_bed`, `unknown`. |
| `exerciseSessions[].exerciseType` | Health Connect exercise type in snake_case. See the list below. A future unmapped code becomes `exercise_<number>`. |
| `unavailableMetrics` | Ids that were not read because permission was missing: `steps`, `distance`, `floors`, `elevation`, `speed`, `power`, `active_calories`, `total_calories`, `basal_metabolic_rate`, `exercise_session`, `wheelchair_pushes`, `sleep_session`, `weight`, `body_fat`, `height`, `lean_body_mass`, `bone_mass`, `body_water_mass`, `heart_rate`, `resting_heart_rate`, `heart_rate_variability`, `oxygen_saturation`, `blood_pressure`, `blood_glucose`, `body_temperature`, `basal_body_temperature`, `respiratory_rate`, `vo2_max`, `nutrition`, `hydration`. |

Exercise types: `other_workout`, `badminton`, `baseball`, `basketball`, `biking`, `biking_stationary`, `boot_camp`, `boxing`, `calisthenics`, `cricket`, `dancing`, `elliptical`, `exercise_class`, `fencing`, `football_american`, `football_australian`, `frisbee_disc`, `golf`, `guided_breathing`, `gymnastics`, `handball`, `high_intensity_interval_training`, `hiking`, `ice_hockey`, `ice_skating`, `martial_arts`, `paddling`, `paragliding`, `pilates`, `racquetball`, `rock_climbing`, `roller_hockey`, `rowing`, `rowing_machine`, `rugby`, `running`, `running_treadmill`, `sailing`, `scuba_diving`, `skating`, `skiing`, `snowboarding`, `snowshoeing`, `soccer`, `softball`, `squash`, `stair_climbing`, `stair_climbing_machine`, `strength_training`, `stretching`, `surfing`, `swimming_open_water`, `swimming_pool`, `table_tennis`, `tennis`, `volleyball`, `walking`, `water_polo`, `weightlifting`, `wheelchair`, `yoga`.

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
