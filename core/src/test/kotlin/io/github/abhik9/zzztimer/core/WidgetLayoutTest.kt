package io.github.abhik9.zzztimer.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WidgetLayoutTest {

    @Test
    fun `layouts follow the size`() {
        assertEquals(WidgetLayout.ICON, WidgetLayout.forSize(40, 40))
        assertEquals(WidgetLayout.ICON, WidgetLayout.forSize(70, 50))
        assertEquals(WidgetLayout.STACKED, WidgetLayout.forSize(70, 80))
        assertEquals(WidgetLayout.STACKED, WidgetLayout.forSize(100, 100))
        assertEquals(WidgetLayout.WIDE, WidgetLayout.forSize(130, 50))
        assertEquals(WidgetLayout.WIDE, WidgetLayout.forSize(250, 110))
    }

    @Test
    fun `a too small widget shows the icon, an unknown size the default layout`() {
        assertEquals(WidgetLayout.ICON, WidgetLayout.forSize(30, 30))
        assertEquals(WidgetLayout.STACKED, WidgetLayout.forSize(0, 0))
        assertEquals(WidgetLayout.STACKED, WidgetLayout.forSize(120, 0))
    }

    @Test
    fun `larger layouts take more room, so that the largest fitting one is also the most detailed`() {
        val areas = WidgetLayout.entries.map { it.areaDp }
        assertEquals(areas.sorted().distinct(), areas)
    }
}
