package com.ethran.notable.gestures

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscreteZoomTest {

    @Test
    fun `zooming in steps from half to original to double`() {
        assertEquals(1.0f, nextDiscreteZoom(current = 0.5f, zoomIn = true), 0f)
        assertEquals(2.0f, nextDiscreteZoom(current = 1.0f, zoomIn = true), 0f)
    }

    @Test
    fun `zooming out steps from double to original to half`() {
        assertEquals(1.0f, nextDiscreteZoom(current = 2.0f, zoomIn = false), 0f)
        assertEquals(0.5f, nextDiscreteZoom(current = 1.0f, zoomIn = false), 0f)
    }

    @Test
    fun `zoom stays at the ends of the range`() {
        assertEquals(2.0f, nextDiscreteZoom(current = 2.0f, zoomIn = true), 0f)
        assertEquals(0.5f, nextDiscreteZoom(current = 0.5f, zoomIn = false), 0f)
    }

    @Test
    fun `a level between steps moves to the nearest step in the pinch direction`() {
        // 0.75 was the old zoomed-out level and may still be saved for a page.
        assertEquals(1.0f, nextDiscreteZoom(current = 0.75f, zoomIn = true), 0f)
        assertEquals(0.5f, nextDiscreteZoom(current = 0.75f, zoomIn = false), 0f)
    }

    @Test
    fun `a level outside the range moves back into it`() {
        assertEquals(2.0f, nextDiscreteZoom(current = 4.0f, zoomIn = false), 0f)
        assertEquals(0.5f, nextDiscreteZoom(current = 0.1f, zoomIn = true), 0f)
    }

    @Test
    fun `float error around a step does not skip or repeat it`() {
        assertEquals(2.0f, nextDiscreteZoom(current = 1.0000001f, zoomIn = true), 0f)
        assertEquals(0.5f, nextDiscreteZoom(current = 0.9999999f, zoomIn = false), 0f)
    }
}
