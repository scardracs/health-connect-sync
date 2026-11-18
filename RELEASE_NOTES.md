# Salute Sync v1.0.0

First sideload build. The app reads Health Connect (including data Samsung Health syncs into it) and POSTs one JSON payload per day to a webhook you configure.

The phone UI is Italian. Install steps, the JSON schema, and the sideload signing key are in the [README](README.md#install-on-a-phone).

Attach `salute-sync-1.0.0.apk` to this release. It is signed with the public sideload key documented in the README (`keystore/salute-sync.jks`, alias `salutesync`, password `salutesync`). That key is for installing and updating outside the Play Store, not for publishing there.
