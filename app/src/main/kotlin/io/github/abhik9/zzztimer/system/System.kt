package io.github.abhik9.zzztimer.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.net.toUri
import io.github.abhik9.zzztimer.R
import io.github.abhik9.zzztimer.core.Requirement

private const val TAG = "System"

/** Where the user can resolve a [Requirement]. */
fun Context.settingsIntent(requirement: Requirement): Intent = when (requirement) {
    Requirement.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    Requirement.EXACT_ALARMS ->
        if (SDK_INT >= S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri())
        else appDetailsIntent()
}

/** Fallback for OEM builds missing a specific settings screen. */
fun Context.appDetailsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())

/**
 * Starts [intent], or the app details settings when no activity handles it.
 */
fun Context.startSettings(intent: Intent, start: (Intent) -> Unit = { startActivity(it) }) {
    try {
        start(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No activity for $intent", e)
        runCatching { start(appDetailsIntent()) }.onFailure { Log.w(TAG, "No app details settings", it) }
    }
}

@get:StringRes
val Requirement.message: Int
    get() = when (this) {
        Requirement.NOTIFICATIONS -> R.string.requirement_notifications_toast
        Requirement.EXACT_ALARMS -> R.string.requirement_alarms_toast
    }

fun Context.toast(@StringRes message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
