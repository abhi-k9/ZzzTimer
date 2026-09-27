package io.github.abhik9.zzztimer.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.net.toUri
import io.github.abhik9.zzztimer.R
import io.github.abhik9.zzztimer.core.Requirement
import io.github.abhik9.zzztimer.core.StartResult
import io.github.abhik9.zzztimer.diagnostics.diagnostics

/** Where the user can resolve a [Requirement]. */
fun Context.settingsIntent(requirement: Requirement): Intent = when (requirement) {
    Requirement.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    Requirement.EXACT_ALARMS ->
        if (SDK_INT >= S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri())
        } else {
            appDetailsIntent()
        }
}

/** Fallback for OEM builds missing a specific settings screen. */
private fun Context.appDetailsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())

/**
 * Starts [intent], or the app details settings when no activity handles it.
 */
fun Context.startSettings(intent: Intent, start: (Intent) -> Unit = { startActivity(it) }) {
    try {
        start(intent)
    } catch (e: ActivityNotFoundException) {
        diagnostics.warn("settings: no activity for $intent", e)
        runCatching { start(appDetailsIntent()) }.onFailure { diagnostics.warn("settings: no app details", it) }
    }
}

@get:StringRes
val Requirement.title: Int
    get() = when (this) {
        Requirement.NOTIFICATIONS -> R.string.requirement_notifications_title
        Requirement.EXACT_ALARMS -> R.string.requirement_alarms_title
    }

@get:StringRes
val Requirement.explanation: Int
    get() = when (this) {
        Requirement.NOTIFICATIONS -> R.string.requirement_notifications_explanation
        Requirement.EXACT_ALARMS -> R.string.requirement_alarms_explanation
    }

fun Context.toast(@StringRes message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

/** Tells the user why a timer operation could not run, when it was [StartResult.Blocked]. */
fun Context.reportBlocked(result: StartResult?) {
    if (result is StartResult.Blocked) toast(result.requirement.title)
}
