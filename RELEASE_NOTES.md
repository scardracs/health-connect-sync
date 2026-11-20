# Salute Sync v1.3.1

Fixes a crash on Android 14 and later, including Samsung phones where Health Connect lives in system settings. Tapping "Open Health Connect settings" started `android.health.connect.action.MANAGE_HEALTH_PERMISSIONS` with `FLAG_ACTIVITY_NEW_TASK` and only caught `ActivityNotFoundException`. On those phones the activity is not exported to an implicit start, so `startActivity` throws `SecurityException` and the app closed.

The button now tries, in order: the Android 14 permission screen (`HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS` with `EXTRA_PACKAGE_NAME`, from the calling activity, then the resolved component), `HealthConnectClient.getHealthConnectManageDataIntent`, the Health Connect settings action, the Play Store page for Health Connect, and this app's system details screen. Every launch is caught. If nothing opens, a short toast is shown in the current language. The same guard covers the Health Connect install button and the battery-optimization button.

`versionCode` 5, same sideload key as 1.3.0. Payload schema v3 is unchanged. Attach `salute-sync-1.3.1.apk`.
