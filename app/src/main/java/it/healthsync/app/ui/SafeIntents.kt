package it.healthsync.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.health.connect.client.HealthConnectClient
import it.healthsync.app.domain.ExternalScreens

/**
 * Opens an external screen. The platform Health Connect permission activity on Android 14
 * (including Samsung system Health Connect) often throws [SecurityException] for an implicit
 * intent, and [ActivityNotFoundException] when nothing handles it. Callers must try the next
 * [ExternalScreens.Step] instead of letting either exception escape.
 */
internal fun Context.startExternal(step: ExternalScreens.Step) {
    val intent = when (step.kind) {
        ExternalScreens.Kind.SDK_MANAGE_DATA -> HealthConnectClient.getHealthConnectManageDataIntent(this)
        else -> Intent(step.action).apply {
            step.packageExtra?.let { putExtra(Intent.EXTRA_PACKAGE_NAME, it) }
            step.data?.let { data = Uri.parse(it) }
        }
    }
    try {
        launch(intent, newTask = findActivity() == null)
    } catch (first: Exception) {
        if (step.kind != ExternalScreens.Kind.PLATFORM_PERMISSIONS || intent.component != null) {
            throw first
        }
        val info = try {
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        } catch (_: Exception) {
            null
        } ?: throw first
        intent.component = ComponentName(info.packageName, info.name)
        launch(intent, newTask = findActivity() == null)
    }
}

internal fun Context.toast(@StringRes message: Int) {
    Toast.makeText(this, getString(message), Toast.LENGTH_LONG).show()
}

private fun Context.launch(intent: Intent, newTask: Boolean) {
    if (newTask) {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } else {
        // The Android 14 permission screen expects the caller's activity token.
        // FLAG_ACTIVITY_NEW_TASK makes that launch look like a background start and is denied.
        findActivity()?.startActivity(intent) ?: startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
