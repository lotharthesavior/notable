package com.ethran.notable.editor.utils

import androidx.compose.ui.geometry.Offset
import kotlin.math.min

/**
 * Where a page's viewport may scroll to.
 *
 * A bounded page keeps its top-left corner at the page origin, so scroll never goes below zero.
 * An infinite canvas drops that floor and lets the viewport move above and left of the origin,
 * which means page coordinates (stroke points, image positions) can be negative.
 */
object CanvasBounds {

    /** Smallest scroll value allowed on either axis. */
    fun minScroll(infinite: Boolean): Float = if (infinite) Float.NEGATIVE_INFINITY else 0f

    /** Clamps [scroll] to the allowed range for the page. */
    fun clampScroll(scroll: Offset, infinite: Boolean): Offset {
        val floor = minScroll(infinite)
        return Offset(scroll.x.coerceAtLeast(floor), scroll.y.coerceAtLeast(floor))
    }

    /**
     * Trims [delta] (page coordinates) so that `scroll + delta` stays in the allowed range.
     * Returns the delta that can actually be applied.
     */
    fun limitScrollDelta(scroll: Offset, delta: Offset, infinite: Boolean): Offset {
        if (infinite) return delta
        return clampScroll(scroll + delta, infinite) - scroll
    }

    /**
     * Top-left corner of the area exports and thumbnails must cover: the page origin, or further
     * up and left when content was drawn at negative coordinates.
     */
    fun contentOrigin(minLeft: Float?, minTop: Float?): Offset =
        Offset(min(0f, minLeft ?: 0f), min(0f, minTop ?: 0f))
}
