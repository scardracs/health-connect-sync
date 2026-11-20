package it.healthsync.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalScreensTest {
    @Test
    fun android14TriesPlatformPermissionsBeforeSdkAndStore() {
        val steps = ExternalScreens.healthPermissionSteps("it.healthsync.app", sdkInt = 34)
        assertEquals(ExternalScreens.Kind.PLATFORM_PERMISSIONS, steps[0].kind)
        assertEquals(ExternalScreens.ACTION_MANAGE_HEALTH_PERMISSIONS, steps[0].action)
        assertEquals("it.healthsync.app", steps[0].packageExtra)
        assertEquals(ExternalScreens.Kind.SDK_MANAGE_DATA, steps[1].kind)
        assertEquals(ExternalScreens.ACTION_HEALTH_CONNECT_SETTINGS, steps[2].action)
        assertEquals(ExternalScreens.MARKET_URI, steps[3].data)
        assertEquals(ExternalScreens.PLAY_STORE_URI, steps[4].data)
        assertEquals("package:it.healthsync.app", steps[5].data)
        assertEquals(ExternalScreens.ACTION_APP_DETAILS, steps[5].action)
    }

    @Test
    fun belowAndroid14SkipsThePlatformPermissionAction() {
        val steps = ExternalScreens.healthPermissionSteps("it.healthsync.app", sdkInt = 33)
        assertTrue(steps.none { it.kind == ExternalScreens.Kind.PLATFORM_PERMISSIONS })
        assertEquals(ExternalScreens.Kind.SDK_MANAGE_DATA, steps.first().kind)
    }

    @Test
    fun securityExceptionFallsThroughToTheNextScreen() {
        val steps = ExternalScreens.healthPermissionSteps("it.healthsync.app", sdkInt = 34)
        val attempted = mutableListOf<ExternalScreens.Kind>()
        val opened = ExternalScreens.launchFirst(steps) { step ->
            attempted += step.kind
            if (step.kind == ExternalScreens.Kind.PLATFORM_PERMISSIONS) {
                throw SecurityException("not exported")
            }
            if (step.kind == ExternalScreens.Kind.SDK_MANAGE_DATA) {
                throw IllegalStateException("no activity")
            }
        }
        assertEquals(ExternalScreens.Kind.ACTION, opened?.kind)
        assertEquals(ExternalScreens.ACTION_HEALTH_CONNECT_SETTINGS, opened?.action)
        assertEquals(
            listOf(
                ExternalScreens.Kind.PLATFORM_PERMISSIONS,
                ExternalScreens.Kind.SDK_MANAGE_DATA,
                ExternalScreens.Kind.ACTION,
            ),
            attempted,
        )
    }

    @Test
    fun everyFailureReturnsNullInsteadOfThrowing() {
        val steps = ExternalScreens.healthInstallSteps("it.healthsync.app")
        val opened = ExternalScreens.launchFirst(steps) { throw SecurityException("blocked") }
        assertNull(opened)
        assertEquals(3, steps.size)
    }

    @Test
    fun batteryRequestFallsBackToTheBatterySettingsList() {
        val steps = ExternalScreens.batterySteps("it.healthsync.app")
        assertEquals("package:it.healthsync.app", steps[0].data)
        val opened = ExternalScreens.launchFirst(steps) { step ->
            if (step.action == ExternalScreens.ACTION_BATTERY_REQUEST) {
                throw SecurityException("denied")
            }
        }
        assertEquals(ExternalScreens.ACTION_BATTERY_SETTINGS, opened?.action)
    }
}
