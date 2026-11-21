# Salute Sync v1.4.0

## English

Fixes the language switch and adds a one-time welcome screen. Choosing English or Italian did nothing, including after the app was closed and opened again. On Android 13 and later, `AppCompatDelegate.setApplicationLocales` only writes the language when an `AppCompatActivity` is on screen. Both activities were `ComponentActivity`, so the call returned without saving anything.

The activities now extend `AppCompatActivity`, and the choice is written straight to `LocaleManager` (the same store as Settings > Apps > Salute Sync > Language). Below Android 13 it is stored by AppCompat and still applied immediately. The screen, toasts, and later sync messages follow the new language.

The first launch, including an update from an older version, shows a dialog: pick Italiano or English, grant Health Connect read permissions, open Health Connect, and leave the 06:00 sync on or off. **Skip** closes it and does not turn the morning sync off. It is not shown again. The same choices stay in Settings. Dates stay DD-MM-YYYY. The README instructions are English.

`versionCode` 8, same sideload key. Payload schema v3 is unchanged. Attach `salute-sync-1.4.0.apk`.

## Italiano

Corregge il cambio lingua e aggiunge una schermata di benvenuto, una sola volta. Scegliere English o Italiano non cambiava nulla, nemmeno chiudendo e riaprendo l'app. Da Android 13 in poi, `AppCompatDelegate.setApplicationLocales` scrive la lingua solo se è attiva un'`AppCompatActivity`. Entrambe le activity erano `ComponentActivity`, quindi la chiamata usciva senza salvare nulla.

Le activity ora estendono `AppCompatActivity`, e la scelta viene scritta direttamente in `LocaleManager` (lo stesso archivio di Impostazioni > App > Salute Sync > Lingua). Sotto Android 13 la salva AppCompat e viene applicata subito. Schermate, toast e i messaggi delle sincronizzazioni successive seguono la nuova lingua.

Al primo avvio, anche aggiornando da una versione precedente, compare una finestra: scegli Italiano o English, concedi i permessi di lettura di Health Connect, apri Health Connect, e lascia attiva o no la sync delle 06:00. **Salta** la chiude e non spegne la sync del mattino. Non viene più mostrata. Le stesse scelte restano in Impostazioni. Le date restano GG-MM-AAAA. Le istruzioni del README sono in inglese.

`versionCode` 8, stessa chiave di sideload. Lo schema del payload v3 non cambia. Allegare `salute-sync-1.4.0.apk`.

# Salute Sync v1.3.3

## English

Removes the warning that appeared when Health Connect returned only calories. Calories alone do not mean read permissions are missing; there may simply be no other data. Denied Health Connect read permissions are still reported as before. Dates stay DD-MM-YYYY. The v1.3.1 settings-launch fix and the v1.3.2 dependencies are unchanged.

`versionCode` 7, same sideload key. Payload schema v3 is unchanged. Attach `salute-sync-1.3.3.apk`.

## Italiano

Rimuove l'avviso che compariva quando Health Connect restituiva solo calorie. Le sole calorie non significano che manchino i permessi di lettura: può semplicemente non esserci altro dato. I permessi di lettura negati da Health Connect restano segnalati come prima. Le date restano GG-MM-AAAA. La correzione della v1.3.1 per aprire le impostazioni e le dipendenze della v1.3.2 sono invariate.

`versionCode` 7, stessa chiave di sideload. Lo schema del payload v3 non cambia. Allegare `salute-sync-1.3.3.apk`.

# Salute Sync v1.3.2

## English

Dependency update only. No functional changes. Dates on screen stay DD-MM-YYYY, and the v1.3.1 fix for opening Health Connect settings is unchanged.

Updated to the latest stable versions: Android Gradle Plugin 9.5.2, Gradle 9.9.2, Jetpack Compose BOM 2026.11.01 (Compose UI 1.13.0, Material3 1.5.0, compileSdk 37.1), AndroidX Core 1.19.1, AppCompat 1.8.0, and Lifecycle 2.11.1. Kotlin stays 2.4.21. targetSdk stays 37 and minSdk stays 28.

`versionCode` 6, same sideload key. Payload schema v3 is unchanged. Attach `salute-sync-1.3.2.apk`.

## Italiano

Solo aggiornamento delle dipendenze. Nessuna modifica funzionale. Le date mostrate restano GG-MM-AAAA e la correzione della v1.3.1 per aprire le impostazioni di Health Connect è invariata.

Versioni stabili aggiornate: Android Gradle Plugin 9.5.2, Gradle 9.9.2, Jetpack Compose BOM 2026.11.01 (Compose UI 1.13.0, Material3 1.5.0, compileSdk 37.1), AndroidX Core 1.19.1, AppCompat 1.8.0 e Lifecycle 2.11.1. Kotlin resta 2.4.21. targetSdk resta 37 e minSdk resta 28.

`versionCode` 6, stessa chiave di sideload. Lo schema del payload v3 non cambia. Allegare `salute-sync-1.3.2.apk`.

# Salute Sync v1.3.1

Fixes a crash on Android 14 and later, including Samsung phones where Health Connect lives in system settings. Tapping "Open Health Connect settings" started `android.health.connect.action.MANAGE_HEALTH_PERMISSIONS` with `FLAG_ACTIVITY_NEW_TASK` and only caught `ActivityNotFoundException`. On those phones the activity is not exported to an implicit start, so `startActivity` throws `SecurityException` and the app closed.

The button now tries, in order: the Android 14 permission screen (`HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS` with `EXTRA_PACKAGE_NAME`, from the calling activity, then the resolved component), `HealthConnectClient.getHealthConnectManageDataIntent`, the Health Connect settings action, the Play Store page for Health Connect, and this app's system details screen. Every launch is caught. If nothing opens, a short toast is shown in the current language. The same guard covers the Health Connect install button and the battery-optimization button.

`versionCode` 5, same sideload key as 1.3.0. Payload schema v3 is unchanged. Attach `salute-sync-1.3.1.apk`.
