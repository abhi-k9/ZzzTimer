package io.github.abhik9.zzztimer.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.abhik9.zzztimer.control.resolveFromControl
import io.github.abhik9.zzztimer.core.ControlOutcome
import io.github.abhik9.zzztimer.core.toggleControl
import io.github.abhik9.zzztimer.diagnostics.diagnostics
import io.github.abhik9.zzztimer.sleepTimer

/**
 * Handles taps on the [SleepWidget], like the Quick Settings tile does. Not exported: only reachable through the
 * widget's own [PendingIntent].
 */
class WidgetTapReceiver : BroadcastReceiver() {

    companion object {
        fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(context, 0, Intent(context, WidgetTapReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onReceive(context: Context, intent: Intent) {
        context.diagnostics.record { "widget: tap" }
        when (val outcome = context.sleepTimer().toggleControl()) {
            is ControlOutcome.Show -> SleepWidget.update(context, outcome.state)
            // The launcher sent the tap, which lets the app start an activity right after.
            is ControlOutcome.Resolve -> context.resolveFromControl(outcome.requirement) {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
