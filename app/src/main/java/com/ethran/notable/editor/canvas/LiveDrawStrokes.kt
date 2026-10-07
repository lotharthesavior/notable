package com.ethran.notable.editor.canvas

import com.ethran.notable.data.db.StrokePoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Parses the LIVE_DRAW stroke file: a JSON array of strokes, each an array of [x, y] page
 * coordinates, e.g. [[[100, 100], [400, 100]], [[100, 200], [400, 300], [700, 200]]].
 * Extra values after x and y are ignored. Throws on malformed input.
 */
internal fun parseLiveDrawStrokes(json: String): List<List<StrokePoint>> =
    Json.parseToJsonElement(json).jsonArray.map { stroke ->
        stroke.jsonArray.map { point ->
            val xy = point.jsonArray
            require(xy.size >= 2) { "point needs x and y: $point" }
            StrokePoint(x = xy[0].jsonPrimitive.float, y = xy[1].jsonPrimitive.float)
        }
    }

/**
 * Inserts evenly spaced points so no segment is longer than [maxStep]. The pen renderers
 * draw a stroke with only its two end points as a dot, so long straight segments need
 * intermediate points to appear as lines.
 */
internal fun densifyStroke(points: List<StrokePoint>, maxStep: Float = 4f): List<StrokePoint> {
    if (points.size < 2) return points
    val out = ArrayList<StrokePoint>(points.size)
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        val n = ceil(hypot(b.x - a.x, b.y - a.y) / maxStep).toInt().coerceAtLeast(1)
        for (k in 0 until n) {
            val t = k.toFloat() / n
            out.add(StrokePoint(x = a.x + (b.x - a.x) * t, y = a.y + (b.y - a.y) * t))
        }
    }
    out.add(points.last())
    return out
}
