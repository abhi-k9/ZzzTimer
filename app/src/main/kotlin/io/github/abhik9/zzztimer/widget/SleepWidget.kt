package io.github.abhik9.zzztimer.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import io.github.abhik9.zzztimer.R
import io.github.abhik9.zzztimer.control.controlLabel
import io.github.abhik9.zzztimer.core.ControlState
import io.github.abhik9.zzztimer.core.WidgetLayout
import io.github.abhik9.zzztimer.diagnostics.diagnostics
import io.github.abhik9.zzztimer.sleepTimer

/**
 * Home screen widget, the counterpart of the Quick Settings tile: a tap starts the default timer, or stops the running
 * one (see [WidgetTapReceiver]). It shows the same [ControlState], in a layout adapted to its size ([WidgetLayout]).
 */
class SleepWidget : AppWidgetProvider() {

    companion object {
        /** Renders every widget with [state], or with the state of the timer when `null`. */
        fun update(context: Context, state: ControlState? = null) = guarded(context) {
            val manager = AppWidgetManager.getInstance(context) ?: return@guarded
            render(context, manager, manager.getAppWidgetIds(ComponentName(context, SleepWidget::class.java)), state)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray, state: ControlState?) {
            if (ids.isEmpty()) return
            val current = state ?: ControlState.of(context.sleepTimer().current())
            context.diagnostics.record { "widget: $current on ${ids.size} widget(s)" }
            for (id in ids) manager.updateAppWidget(id, views(context, manager, id, current))
        }

        private fun views(context: Context, manager: AppWidgetManager, id: Int, state: ControlState): RemoteViews =
            if (SDK_INT >= S) {
                // Responsive layouts: the launcher picks the one fitting the widget, also while it is resized.
                RemoteViews(WidgetLayout.entries.associate { it.size to views(context, it, state) })
            } else {
                // Portrait size: the narrowest width and the tallest height.
                val options = manager.getAppWidgetOptions(id)
                val layout = WidgetLayout.forSize(options.getInt(OPTION_APPWIDGET_MIN_WIDTH), options.getInt(OPTION_APPWIDGET_MAX_HEIGHT))
                views(context, layout, state)
            }

        private fun views(context: Context, layout: WidgetLayout, state: ControlState) =
            RemoteViews(context.packageName, layout.resource).apply {
                val label = context.controlLabel(state)
                // The icon colors follow its enabled state (widget_icon_background, widget_icon_tint): resolved by the
                // launcher, they follow its dark mode and the dynamic colors.
                setBoolean(R.id.widget_icon, "setEnabled", state is ControlState.Running)
                if (layout != WidgetLayout.ICON) setTextViewText(R.id.widget_label, label)
                setContentDescription(android.R.id.background, context.getString(R.string.widget_content_description, label))
                setOnClickPendingIntent(android.R.id.background, WidgetTapReceiver.pendingIntent(context))
            }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = guarded(context) {
        render(context, manager, ids, state = null)
    }

    // Since Android 12 the responsive layouts already follow the size.
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        if (SDK_INT < S) guarded(context) { render(context, manager, intArrayOf(id), state = null) }
    }
}

/** Smallest size of the layout, in dp. */
private val WidgetLayout.size: SizeF get() = SizeF(minWidthDp.toFloat(), minHeightDp.toFloat())

@get:LayoutRes
private val WidgetLayout.resource: Int
    get() = when (this) {
        WidgetLayout.ICON -> R.layout.widget_icon
        WidgetLayout.STACKED -> R.layout.widget_stacked
        WidgetLayout.WIDE -> R.layout.widget_wide
    }

/** The launcher may be gone or restarting: it renders the widgets again on its return. */
private inline fun guarded(context: Context, block: () -> Unit) {
    try {
        block()
    } catch (e: RuntimeException) {
        context.diagnostics.warn("widget: update failed", e)
    }
}
