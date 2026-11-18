# Salute Sync v1.2.0

The webhook keeps only about 4,000 characters of each body and drops bursts, even when it answers HTTP 200. This build stays inside both limits.

- Every POST is at most 3,500 bytes. Days use short keys. A request holds as many days as fit, or one day when a day is large.
- Sends are strictly sequential, at least 60 seconds apart, through WorkManager, and resume after a failure.
- Backfill skips days that contain only calorie estimates. The default start is the earliest day with any other data. You can set a start date. **Reimporta storico** clears progress and starts over.
- The screen lists per-type counts from the last read. If steps, sleep, weight, and workouts are all zero while calories exist, it points at Health Connect → App permissions → Samsung Health.

`versionCode` 3, same sideload key as 1.0.0 and 1.1.0. Install steps and the key map are in the [README](README.md#install-on-a-phone). Attach `salute-sync-1.2.0.apk`.
