# Salute Sync v1.3.0

The phone UI can be Italian or English. Italian is the default. Change it in Settings; it applies immediately and is remembered. Dates shown in the app use DD-MM-YYYY (and DD-MM-YYYY HH:mm when a time is included). Dates inside the webhook JSON stay ISO `yyyy-MM-dd`.

Settings now holds the language, the webhook URL and masked key (same stored fields as before), and the Health Connect read permissions, including a per-type granted status, a request button, and a shortcut to Health Connect. The main screen keeps sync status, Sync now, and Reimport history, plus a short notice when the webhook or permissions are missing.

Payload schema v3, the 3,500-byte cap, and the 60-second send gap are unchanged. `versionCode` 4, same sideload key as previous builds. Install steps are in the [README](README.md#install-on-a-phone). Attach `salute-sync-1.3.0.apk`.
