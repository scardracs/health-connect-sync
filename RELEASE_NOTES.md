# Salute Sync v1.1.0

Full history import. The first sync, and **Importa storico completo**, reads every supported Health Connect type from the earliest available data to now and POSTs it in monthly chunks.

Each history payload has `"kind": "backfill"` plus `chunk.index`, `chunk.total`, and the chunk start and end. Progress is saved on the phone, so a failed chunk can be retried without resending chunks that already succeeded. Chunks larger than about 1.5 MB are split into weeks, then days.

The app requests `READ_HEALTH_DATA_HISTORY` when the device supports it. Without that permission, Health Connect only allows data from 30 days before the first read grant, and the Italian UI says so. Daily syncs after the import are `"kind": "incremental"`.

Schema version is 2. New daily totals include floors, elevation, speed, power, cadence, wheelchair pushes, and basal calories. New readings include height, lean mass, bone mass, body water, temperature, basal temperature, blood pressure, blood glucose, respiratory rate, and VO2 max. Menstruation-related types are not read.

Install steps and the public sideload key are in the [README](README.md#install-on-a-phone). This build is `versionCode` 2, signed with the same key as 1.0.0, so it updates over that install. Attach `salute-sync-1.1.0.apk`.
