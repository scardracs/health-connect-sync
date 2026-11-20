package it.healthsync.app.domain

/**
 * Screens the app may open outside itself, in the order they should be tried.
 * Android types stay out of this file so the order can be unit tested.
 */
object ExternalScreens {
    const val UPSIDE_DOWN_CAKE = 34

    const val ACTION_MANAGE_HEALTH_PERMISSIONS = "android.health.connect.action.MANAGE_HEALTH_PERMISSIONS"
    const val ACTION_MANAGE_HEALTH_DATA = "androidx.health.ACTION_MANAGE_HEALTH_DATA"
    const val ACTION_HEALTH_CONNECT_SETTINGS = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
    const val ACTION_VIEW = "android.intent.action.VIEW"
    const val ACTION_APP_DETAILS = "android.settings.APPLICATION_DETAILS_SETTINGS"
    const val ACTION_BATTERY_REQUEST = "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
    const val ACTION_BATTERY_SETTINGS = "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"

    const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
    const val MARKET_URI = "market://details?id=$HEALTH_CONNECT_PACKAGE"
    const val PLAY_STORE_URI = "https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE"

    enum class Kind {
        /** Android 14+ platform permission screen. */
        PLATFORM_PERMISSIONS,

        /** [androidx.health.connect.client.HealthConnectClient.getHealthConnectManageDataIntent]. */
        SDK_MANAGE_DATA,

        ACTION,
    }

    data class Step(
        val kind: Kind,
        val action: String? = null,
        val packageExtra: String? = null,
        val data: String? = null,
    )

    fun healthPermissionSteps(appPackage: String, sdkInt: Int): List<Step> {
        val steps = mutableListOf<Step>()
        if (sdkInt >= UPSIDE_DOWN_CAKE) {
            steps += Step(
                kind = Kind.PLATFORM_PERMISSIONS,
                action = ACTION_MANAGE_HEALTH_PERMISSIONS,
                packageExtra = appPackage,
            )
        }
        steps += Step(kind = Kind.SDK_MANAGE_DATA, action = ACTION_MANAGE_HEALTH_DATA)
        steps += Step(kind = Kind.ACTION, action = ACTION_HEALTH_CONNECT_SETTINGS)
        steps += storeAndAppDetails(appPackage)
        return steps
    }

    fun healthInstallSteps(appPackage: String): List<Step> = storeAndAppDetails(appPackage)

    fun batterySteps(appPackage: String): List<Step> = listOf(
        Step(kind = Kind.ACTION, action = ACTION_BATTERY_REQUEST, data = "package:$appPackage"),
        Step(kind = Kind.ACTION, action = ACTION_BATTERY_SETTINGS),
    )

    /**
     * Starts the first step that does not throw. [ActivityNotFoundException] and
     * [SecurityException] are both [Exception], which is what Android 14 throws when the
     * platform permission activity is missing or not exported.
     */
    fun launchFirst(steps: List<Step>, start: (Step) -> Unit): Step? {
        for (step in steps) {
            try {
                start(step)
                return step
            } catch (_: Exception) {
                // Try the next screen. A toast is the caller's job when nothing starts.
            }
        }
        return null
    }

    private fun storeAndAppDetails(appPackage: String): List<Step> = listOf(
        Step(kind = Kind.ACTION, action = ACTION_VIEW, data = MARKET_URI),
        Step(kind = Kind.ACTION, action = ACTION_VIEW, data = PLAY_STORE_URI),
        Step(kind = Kind.ACTION, action = ACTION_APP_DETAILS, data = "package:$appPackage"),
    )
}
