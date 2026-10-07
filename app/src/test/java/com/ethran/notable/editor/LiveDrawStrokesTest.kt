package com.ethran.notable.editor

import com.ethran.notable.data.db.StrokePoint
import com.ethran.notable.editor.canvas.densifyStroke
import com.ethran.notable.editor.canvas.parseLiveDrawStrokes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class LiveDrawStrokesTest {

    @Test
    fun parsesStrokesAndPoints() {
        val strokes = parseLiveDrawStrokes("[[[1, 2], [3.5, 4]], [[10, 20], [30, 40], [50, 60, 0.7]]]")

        assertEquals(2, strokes.size)
        assertEquals(listOf(StrokePoint(1f, 2f), StrokePoint(3.5f, 4f)), strokes[0])
        assertEquals(StrokePoint(50f, 60f), strokes[1][2])
    }

    @Test
    fun parsesEmptyFile() {
        assertTrue(parseLiveDrawStrokes("[]").isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPointWithoutY() {
        parseLiveDrawStrokes("[[[1]]]")
    }

    @Test(expected = Exception::class)
    fun rejectsMalformedJson() {
        parseLiveDrawStrokes("[[[1, 2]")
    }

    @Test
    fun densifyKeepsEndpointsAndLimitsStep() {
        val line = listOf(StrokePoint(0f, 0f), StrokePoint(100f, 0f))

        val dense = densifyStroke(line, maxStep = 4f)

        assertEquals(StrokePoint(0f, 0f), dense.first())
        assertEquals(StrokePoint(100f, 0f), dense.last())
        assertEquals(26, dense.size)
        dense.zipWithNext().forEach { (a, b) ->
            assertTrue(hypot(b.x - a.x, b.y - a.y) <= 4f + 1e-4f)
        }
    }

    @Test
    fun densifyLeavesShortStrokesAlone() {
        val single = listOf(StrokePoint(5f, 5f))
        assertEquals(single, densifyStroke(single))

        val short = listOf(StrokePoint(0f, 0f), StrokePoint(1f, 1f))
        assertEquals(short, densifyStroke(short, maxStep = 4f))
    }
}
