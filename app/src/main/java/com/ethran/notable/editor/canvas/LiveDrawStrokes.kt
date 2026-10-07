package com.ethran.notable.editor.canvas

import com.ethran.notable.data.db.StrokePoint
import com.ethran.notable.data.model.PageLayer
import com.ethran.notable.data.model.PageLayers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * One stroke of a LIVE_DRAW file. [color] is ARGB; null means the broadcast's color. [layer]
 * is a layer name; null means the broadcast's layer, or the active layer.
 */
internal data class LiveDrawStroke(
    val points: List<StrokePoint>,
    val color: Int? = null,
    val layer: String? = null,
)

/**
 * Parses the LIVE_DRAW stroke file: a JSON array of strokes in page coordinates. Each stroke
 * is either a point array, [[x, y], [x, y], ...], or an object with its own color,
 * {"color": "#RRGGBB" or "#AARRGGBB", "layer": "name", "points": [[x, y], ...]}, where color
 * and layer are optional. Extra values after x and y are ignored. Throws on malformed input.
 */
internal fun parseLiveDrawStrokes(json: String): List<LiveDrawStroke> =
    Json.parseToJsonElement(json).jsonArray.map { stroke ->
        when (stroke) {
            is JsonArray -> LiveDrawStroke(parsePoints(stroke))
            is JsonObject -> LiveDrawStroke(
                points = parsePoints(
                    requireNotNull(stroke["points"]) { "stroke object needs points: $stroke" }
                ),
                color = stroke["color"]?.jsonPrimitive?.content?.let(::parseLiveDrawColor),
                layer = stroke["layer"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
            )
            else -> throw IllegalArgumentException("stroke must be an array or object: $stroke")
        }
    }

private fun parsePoints(points: JsonElement): List<StrokePoint> =
    points.jsonArray.map { point ->
        val xy = point.jsonArray
        require(xy.size >= 2) { "point needs x and y: $point" }
        StrokePoint(x = xy[0].jsonPrimitive.float, y = xy[1].jsonPrimitive.float)
    }

/** Parses "#RRGGBB" (opaque) or "#AARRGGBB" into an ARGB int. */
internal fun parseLiveDrawColor(value: String): Int {
    val hex = value.removePrefix("#")
    require((hex.length == 6 || hex.length == 8) && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
        "color must be #RRGGBB or #AARRGGBB: $value"
    }
    val argb = hex.toLong(16)
    return (if (hex.length == 6) argb or 0xFF000000 else argb).toInt()
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

/**
 * Splits strokes into groups that are drawn and refreshed together, dropping strokes with
 * fewer than two points (nothing to draw). Order is preserved.
 */
internal fun liveDrawBatches(strokes: List<LiveDrawStroke>, batch: Int): List<List<LiveDrawStroke>> =
    strokes.filter { it.points.size >= 2 }.chunked(batch.coerceAtLeast(1))

/**
 * Maps the layer names used by [strokes] (falling back to [defaultLayer]) to layer ids of the
 * page, matching names case-insensitively and adding missing layers on top in order of first use.
 * Returns the new layer list and the id for each name; strokes with no name at all are not
 * in the map and go to the active layer. [usedIds] are the layer ids carried by page content.
 */
internal fun resolveLiveDrawLayers(
    layers: List<PageLayer>,
    strokes: List<LiveDrawStroke>,
    defaultLayer: String?,
    usedIds: Collection<Int> = emptyList(),
): Pair<List<PageLayer>, Map<String, Int>> {
    var result = layers
    val ids = LinkedHashMap<String, Int>()
    val names = (listOfNotNull(defaultLayer?.trim()?.takeIf { it.isNotEmpty() }) +
            strokes.mapNotNull { it.layer }).distinct()
    for (name in names) {
        val layer = PageLayers.findByName(result, name)
            ?: PageLayers.add(result, usedIds, name).also { result = it }.last()
        ids[name] = layer.id
    }
    return result to ids
}
