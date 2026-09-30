package io.github.abhik9.zzztimer.diagnostics

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.BAKLAVA
import android.os.Build.VERSION_CODES.P
import android.os.Build.VERSION_CODES.S
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.pm.PackageInfoCompat
import io.github.abhik9.zzztimer.settings.SettingsStore
import io.github.abhik9.zzztimer.sleep.MediaAccessService
import io.github.abhik9.zzztimer.sleepTimer
import io.github.abhik9.zzztimer.timer.TimerNotification
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * Snapshot of the app and device state, written at the top of an exported diagnostics log.
 * It only contains technical details: no personal data.
 */
internal object DiagnosticsReport {

    fun header(context: Context): String = buildString {
        val app = context.applicationContext
        val info = packageInfo(app)
        val notifications = app.getSystemService(NotificationManager::class.java)
        val power = app.getSystemService(PowerManager::class.java)
        val timer = app.sleepTimer()

        appendLine("ZzzTimer diagnostics")
        appendLine("Generated: ${OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)} [elapsedRealtime ${SystemClock.elapsedRealtime()}]")
        appendLine("App: ${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)}), ${app.packageName}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (${sdkVersion()})")
        appendLine("Notifications enabled: ${notifications.areNotificationsEnabled()}")
        val channel = notifications.getNotificationChannel(TimerNotification.CHANNEL_ID)
        appendLine("Timer channel importance: ${channel?.importance ?: "not created"}")
        val exactAlarms = if (SDK_INT >= S) app.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() else "n/a"
        appendLine("Exact alarms allowed: $exactAlarms")
        appendLine("Notification access (rewind): ${MediaAccessService.isGranted(app)}")
        appendLine("Ignoring battery optimizations: ${power.isIgnoringBatteryOptimizations(app.packageName)}")
        appendLine("Power save mode: ${power.isPowerSaveMode}")
        if (SDK_INT >= P) {
            val bucket = app.getSystemService(UsageStatsManager::class.java).appStandbyBucket
            appendLine("Standby bucket: ${standbyBucket(bucket)}")
        }
        appendLine("Missing requirement: ${timer.missingRequirement() ?: "none"}")
        appendLine("Timer: ${timer.current() ?: "none"}, remaining: ${timer.remaining() ?: "n/a"}")
        appendLine("Settings: ${SettingsStore.from(app).snapshot()}")
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(context: Context): PackageInfo = if (SDK_INT >= TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }

    private fun sdkVersion(): String = if (SDK_INT >= BAKLAVA) "API ${Build.VERSION.SDK_INT_FULL}" else "API $SDK_INT"

    @RequiresApi(P)
    private fun standbyBucket(bucket: Int): String = when (bucket) {
        UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
        UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working set"
        UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
        UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
        else -> "other"
    } + " ($bucket)"
}
