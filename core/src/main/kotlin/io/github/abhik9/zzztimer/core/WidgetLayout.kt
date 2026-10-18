package io.github.abhik9.zzztimer.core

/**
 * Layouts of the home screen widget, from the smallest. Each one needs at least [minWidthDp] × [minHeightDp]: the
 * largest one that fits (by area) is used, which is also how Android 12+ picks among responsive layouts.
 */
enum class WidgetLayout(val minWidthDp: Int, val minHeightDp: Int) {
    /** Only the icon. */
    ICON(minWidthDp = 40, minHeightDp = 40),

    /** The icon above the label: the default, for a single cell. */
    STACKED(minWidthDp = 56, minHeightDp = 64),

    /** The icon next to the app name and the label. */
    WIDE(minWidthDp = 120, minHeightDp = 48),
    ;

    val areaDp: Int get() = minWidthDp * minHeightDp

    fun fits(widthDp: Int, heightDp: Int): Boolean = widthDp >= minWidthDp && heightDp >= minHeightDp

    companion object {
        /** For a widget of [widthDp] × [heightDp]: an unknown size (`0`) gets the default, and a too small one the icon. */
        fun forSize(widthDp: Int, heightDp: Int): WidgetLayout {
            if (widthDp <= 0 || heightDp <= 0) return STACKED
            return entries.filter { it.fits(widthDp, heightDp) }.maxByOrNull { it.areaDp } ?: ICON
        }
    }
}
