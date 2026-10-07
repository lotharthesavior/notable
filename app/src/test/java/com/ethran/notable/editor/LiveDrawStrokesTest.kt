package com.ethran.notable.editor

import com.ethran.notable.data.db.StrokePoint
import com.ethran.notable.data.model.PageLayer
import com.ethran.notable.data.model.PageLayers
import com.ethran.notable.editor.canvas.LiveDrawStroke
import com.ethran.notable.editor.canvas.densifyStroke
import com.ethran.notable.editor.canvas.liveDrawBatches
import com.ethran.notable.editor.canvas.parseLiveDrawColor
import com.ethran.notable.editor.canvas.parseLiveDrawStrokes
import com.ethran.notable.editor.canvas.resolveLiveDrawLayers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class LiveDrawStrokesTest {

    @Test
    fun parsesStrokesAndPoints() {
        val strokes = parseLiveDrawStrokes("[[[1, 2], [3.5, 4]], [[10, 20], [30, 40], [50, 60, 0.7]]]")

        assertEquals(2, strokes.size)
        assertEquals(listOf(StrokePoint(1f, 2f), StrokePoint(3.5f, 4f)), strokes[0].points)
        assertEquals(StrokePoint(50f, 60f), strokes[1].points[2])
        assertNull(strokes[0].color)
    }

    @Test
    fun parsesEmptyFile() {
        assertTrue(parseLiveDrawStrokes("[]").isEmpty())
    }

    @Test
    fun parsesColoredStrokeObjectsMixedWithPlainStrokes() {
        val strokes = parseLiveDrawStrokes(
            """[{"color": "#FF0000", "points": [[0, 0], [5, 5]]}, [[1, 1], [2, 2]], {"points": [[3, 3], [4, 4]]}]"""
        )

        assertEquals(0xFFFF0000.toInt(), strokes[0].color)
        assertEquals(listOf(StrokePoint(0f, 0f), StrokePoint(5f, 5f)), strokes[0].points)
        assertNull(strokes[1].color)
        assertNull(strokes[2].color)
    }

    @Test
    fun parsesColorFormats() {
        assertEquals(0xFF336699.toInt(), parseLiveDrawColor("#336699"))
        assertEquals(0x80336699.toInt(), parseLiveDrawColor("#80336699"))
        assertEquals(0xFFABCDEF.toInt(), parseLiveDrawColor("abcdef"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBadColor() {
        parseLiveDrawColor("#12345")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonHexColor() {
        parseLiveDrawColor("#GGHHII")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsStrokeObjectWithoutPoints() {
        parseLiveDrawStrokes("""[{"color": "#000000"}]""")
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

    @Test
    fun batchesPreserveOrderAndDropUndrawableStrokes() {
        val line = { i: Int -> LiveDrawStroke(listOf(StrokePoint(i.toFloat(), 0f), StrokePoint(i.toFloat(), 1f))) }
        val dot = LiveDrawStroke(listOf(StrokePoint(9f, 9f)))
        val strokes = listOf(line(0), dot, line(1), line(2), line(3), line(4))

        val batches = liveDrawBatches(strokes, batch = 2)

        assertEquals(listOf(2, 2, 1), batches.map { it.size })
        assertEquals(listOf(0f, 1f, 2f, 3f, 4f), batches.flatten().map { it.points[0].x })
    }

    @Test
    fun batchSizeBelowOneDrawsOneStrokeAtATime() {
        val strokes = List(3) { LiveDrawStroke(listOf(StrokePoint(0f, 0f), StrokePoint(1f, 1f))) }

        assertEquals(3, liveDrawBatches(strokes, batch = 0).size)
    }

    @Test
    fun parsesLayerNames() {
        val strokes = parseLiveDrawStrokes(
            """[{"layer": " Sky ", "points": [[0, 0], [1, 1]]}, {"layer": "", "points": [[0, 0], [1, 1]]}, [[0, 0], [1, 1]]]"""
        )

        assertEquals(listOf("Sky", null, null), strokes.map { it.layer })
    }

    @Test
    fun resolvesLayerNamesAndAddsMissingLayersOnTop() {
        val line = listOf(StrokePoint(0f, 0f), StrokePoint(1f, 1f))
        val existing = listOf(PageLayer(id = 0, name = "Layer 1"), PageLayer(id = 1, name = "Sky"))
        val strokes = listOf(
            LiveDrawStroke(line, layer = "horses"),
            LiveDrawStroke(line, layer = "sky"),
            LiveDrawStroke(line),
            LiveDrawStroke(line, layer = "horses"),
        )

        val (layers, ids) = resolveLiveDrawLayers(existing, strokes, defaultLayer = "Ground", usedIds = listOf(5))

        assertEquals(listOf("Layer 1", "Sky", "Ground", "horses"), layers.map { it.name })
        assertEquals(mapOf("Ground" to 6, "horses" to 7, "sky" to 1), ids)
    }

    @Test
    fun withoutLayerNamesTheLayersStayAsTheyAre() {
        val strokes = listOf(LiveDrawStroke(listOf(StrokePoint(0f, 0f), StrokePoint(1f, 1f))))

        val (layers, ids) = resolveLiveDrawLayers(PageLayers.DEFAULT, strokes, defaultLayer = null)

        assertEquals(PageLayers.DEFAULT, layers)
        assertTrue(ids.isEmpty())
    }
}
