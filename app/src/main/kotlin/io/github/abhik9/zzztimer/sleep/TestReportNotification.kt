package io.github.abhik9.zzztimer.sleep

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import io.github.abhik9.zzztimer.R
import io.github.abhik9.zzztimer.core.SleepReport
import io.github.abhik9.zzztimer.core.TestFinding
import io.github.abhik9.zzztimer.core.findings
import io.github.abhik9.zzztimer.core.formatCountdown
import io.github.abhik9.zzztimer.diagnostics.diagnostics
import io.github.abhik9.zzztimer.timer.TimerNotification
import io.github.abhik9.zzztimer.ui.MainActivity
import java.text.NumberFormat

/** Tells the user what a test of the timer end found, see [io.github.abhik9.zzztimer.core.TimerEndTest]. */
internal object TestReportNotification {
    private const val ID = 3

    fun post(context: Context, report: SleepReport) {
        val text = report.findings().joinToString(" ") { context.describe(it) }
        val notification = Notification.Builder(context, TimerNotification.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(context.getString(R.string.test_report_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(MainActivity.pendingIntent(context))
            .setAutoCancel(true)
            .build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(ID, notification)
        } catch (e: SecurityException) {
            // Notifications turned off since the test started.
            context.diagnostics.warn("test: report not posted", e)
        }
    }

    private fun Context.describe(finding: TestFinding): String {
        val numbers = NumberFormat.getIntegerInstance()
        return when (finding) {
            TestFinding.NothingPlaying -> getString(R.string.test_nothing_playing)
            is TestFinding.Faded -> getString(R.string.test_faded, numbers.format(finding.from), numbers.format(finding.to))
            TestFinding.VolumeRefused -> getString(R.string.test_volume_refused)
            TestFinding.NoFade -> getString(R.string.test_no_fade)
            TestFinding.Paused -> getString(R.string.test_paused)
            TestFinding.NotPaused -> getString(R.string.test_not_paused)
            is TestFinding.Rewound -> getString(R.string.test_rewound, formatCountdown(finding.position))
        }
    }
}
