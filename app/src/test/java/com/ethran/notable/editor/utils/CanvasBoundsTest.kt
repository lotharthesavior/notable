package com.ethran.notable.editor.utils

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasBoundsTest {

    @Test
    fun `bounded page clamps scroll at the origin`() {
        assertEquals(Offset(0f, 0f), CanvasBounds.clampScroll(Offset(-40f, -10f), infinite = false))
        assertEquals(Offset(30f, 0f), CanvasBounds.clampScroll(Offset(30f, -10f), infinite = false))
    }

    @Test
    fun `infinite canvas keeps negative scroll`() {
        assertEquals(
            Offset(-4000f, -250f),
            CanvasBounds.clampScroll(Offset(-4000f, -250f), infinite = true)
        )
    }

    @Test
    fun `bounded page trims a delta that would cross the origin`() {
        val applied = CanvasBounds.limitScrollDelta(
            scroll = Offset(10f, 100f),
            delta = Offset(-30f, -40f),
            infinite = false,
        )
        assertEquals(Offset(-10f, -40f), applied)
    }

    @Test
    fun `bounded page at the origin cannot move further up or left`() {
        val applied = CanvasBounds.limitScrollDelta(
            scroll = Offset.Zero,
            delta = Offset(-5f, -5f),
            infinite = false,
        )
        assertEquals(Offset.Zero, applied)
    }

    @Test
    fun `infinite canvas applies the full delta past the origin`() {
        val applied = CanvasBounds.limitScrollDelta(
            scroll = Offset(10f, 0f),
            delta = Offset(-30f, -40f),
            infinite = true,
        )
        assertEquals(Offset(-30f, -40f), applied)
    }

    @Test
    fun `content origin stays at the page origin for non-negative content`() {
        assertEquals(Offset.Zero, CanvasBounds.contentOrigin(minLeft = 12f, minTop = 80f))
        assertEquals(Offset.Zero, CanvasBounds.contentOrigin(minLeft = null, minTop = null))
    }

    @Test
    fun `content origin moves to content drawn above and left of the page`() {
        assertEquals(
            Offset(-300f, -1200f),
            CanvasBounds.contentOrigin(minLeft = -300f, minTop = -1200f)
        )
        assertEquals(Offset(0f, -50f), CanvasBounds.contentOrigin(minLeft = 20f, minTop = -50f))
    }
}
