package com.ethran.notable.gestures

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val T0 = 1_000L

class GestureClassifierTest {

    private var clockTime = T0
    private val tracker = PointerTracker(now = { clockTime })

    // Density 1 ⇒ dp values equal px values: tap tolerance 15, two-finger
    // tap tolerance 20, swipe 160, multi-finger swipe 100, smooth-scroll
    // entry 100, swipe noise floor 10.
    private val thresholds = GestureThresholds(Density(1f))
    private val noFlags = GestureFlags(smoothScroll = false, continuousZoom = false)

    private fun classify(
        mode: GestureMode = GestureMode.Normal,
        flags: GestureFlags = noFlags,
    ) = classifyGesture(tracker, mode, flags, thresholds)

    // --- Taps ---------------------------------------------------------------

    @Test
    fun `quick stationary one-finger touch is a tap`() {
        tracker.down(1, 50f, 50f, T0)
        tracker.up(1, 53f, 53f, T0 + 50)
        assertEquals(listOf(GestureEvent.Tap(fingers = 1)), classify())
    }

    @Test
    fun `slow one-finger touch is not a tap`() {
        tracker.down(1, 50f, 50f, T0)
        tracker.up(1, 50f, 50f, T0 + 150)
        assertTrue(classify().isEmpty())
    }

    @Test
    fun `two-finger tap within the timing window`() {
        tracker.down(1, 100f, 100f, T0)
        tracker.down(2, 200f, 100f, T0 + 10)
        tracker.up(1, 100f, 100f, T0 + 75)
        tracker.up(2, 200f, 100f, T0 + 80)
        assertEquals(listOf(GestureEvent.Tap(fingers = 2)), classify())
    }

    @Test
    fun `two-finger touch shorter than the minimum is contact noise, not a tap`() {
        tracker.down(1, 100f, 100f, T0)
        tracker.down(2, 200f, 100f, T0 + 5)
        tracker.up(1, 100f, 100f, T0 + 12)
        tracker.up(2, 200f, 100f, T0 + 15)
        assertTrue(classify().isEmpty())
    }

    @Test
    fun `three-finger touch emits no tap event`() {
        tracker.down(1, 100f, 100f, T0)
        tracker.down(2, 200f, 100f, T0 + 10)
        tracker.down(3, 300f, 100f, T0 + 15)
        tracker.up(1, 100f, 100f, T0 + 70)
        tracker.up(2, 200f, 100f, T0 + 72)
        tracker.up(3, 300f, 100f, T0 + 75)
        assertTrue(classify().isEmpty())
    }

    // --- Hold (tap-vs-hold timing edges) --------------------------------------

    @Test
    fun `hold triggers at the threshold, not before`() {
        tracker.down(1, 50f, 50f, T0)
        clockTime = T0 + HOLD_THRESHOLD_MS - 1
        assertFalse(isHoldingOneFinger(tracker, thresholds))
        clockTime = T0 + HOLD_THRESHOLD_MS.toLong()
        assertTrue(isHoldingOneFinger(tracker, thresholds))
    }

    @Test
    fun `movement beyond the tap tolerance defeats hold`() {
        tracker.down(1, 50f, 50f, T0)
        tracker.moveTo(1, 70f, 50f, T0 + 100) // 20 px > 15 px tolerance
        clockTime = T0 + 400
        assertFalse(isHoldingOneFinger(tracker, thresholds))
    }

    @Test
    fun `a second finger defeats hold`() {
        tracker.down(1, 50f, 50f, T0)
        tracker.down(2, 60f, 50f, T0 + 50)
        clockTime = T0 + 400
        assertFalse(isHoldingOneFinger(tracker, thresholds))
    }

    // --- QuickNav swipe-up (3+ fingers, app-level) ----------------------------

    @Test
    fun `three fingers swiping up is a quicknav swipe`() {
        tracker.down(1, 100f, 500f, T0)
        tracker.down(2, 200f, 500f, T0 + 10)
        tracker.down(3, 300f, 500f, T0 + 20)
        // All three slide up by 150 px (> 100 px multi-finger threshold).
        tracker.moveTo(1, 100f, 350f, T0 + 120)
        tracker.moveTo(2, 200f, 350f, T0 + 120)
        tracker.moveTo(3, 300f, 350f, T0 + 120)
        assertTrue(isQuickNavSwipe(tracker, thresholds))
    }

    @Test
    fun `a horizontal three-finger swipe is not a quicknav swipe`() {
        tracker.down(1, 100f, 300f, T0)
        tracker.down(2, 200f, 300f, T0 + 10)
        tracker.down(3, 300f, 300f, T0 + 20)
        tracker.moveTo(1, 250f, 290f, T0 + 120)
        tracker.moveTo(2, 350f, 290f, T0 + 120)
        tracker.moveTo(3, 450f, 290f, T0 + 120)
        assertFalse(isQuickNavSwipe(tracker, thresholds))
    }

    @Test
    fun `a short upward travel is not a quicknav swipe`() {
        tracker.down(1, 100f, 500f, T0)
        tracker.down(2, 200f, 500f, T0 + 10)
        tracker.down(3, 300f, 500f, T0 + 20)
        // 60 px up < the 100 px multi-finger threshold.
        tracker.moveTo(1, 100f, 440f, T0 + 120)
        tracker.moveTo(2, 200f, 440f, T0 + 120)
        tracker.moveTo(3, 300f, 440f, T0 + 120)
        assertFalse(isQuickNavSwipe(tracker, thresholds))
    }

    @Test
    fun `two fingers swiping up are not a quicknav swipe`() {
        tracker.down(1, 100f, 500f, T0)
        tracker.down(2, 200f, 500f, T0 + 10)
        tracker.moveTo(1, 100f, 300f, T0 + 120)
        tracker.moveTo(2, 200f, 300f, T0 + 120)
        assertFalse(isQuickNavSwipe(tracker, thresholds))
    }

    // --- Swipes ---------------------------------------------------------------

    @Test
    fun `one-finger horizontal movement past the threshold is a swipe`() {
        tracker.down(1, 100f, 100f, T0)
        tracker.moveTo(1, 350f, 110f, T0 + 150)
        tracker.up(1, 350f, 110f, T0 + 180)
        assertEquals(
            listOf(GestureEvent.Swipe(fingers = 1, direction = GestureEvent.Direction.Right)),
            classify()
        )
    }

    @Test
    fun `leftward movement is a left swipe`() {
        tracker.down(1, 400f, 100f, T0)
        tracker.up(1, 150f, 100f, T0 + 180)
        assertEquals(
            listOf(GestureEvent.Swipe(fingers = 1, direction = GestureEvent.Direction.Left)),
            classify()
        )
    }

    @Test
    fun `three-finger swipe reports three fingers, not two`() {
        tracker.down(1, 100f, 100f, T0)
        tracker.down(2, 100f, 200f, T0 + 10)
        tracker.down(3, 100f, 300f, T0 + 15)
        tracker.moveTo(1, 320f, 100f, T0 + 150)
        tracker.moveTo(2, 330f, 200f, T0 + 150)
        tracker.moveTo(3, 340f, 300f, T0 + 150)
        tracker.up(1, 320f, 100f, T0 + 180)
        tracker.up(2, 330f, 200f, T0 + 182)
        tracker.up(3, 340f, 300f, T0 + 185)
        assertEquals(
            listOf(GestureEvent.Swipe(fingers = 3, direction = GestureEvent.Direction.Right)),
            classify()
        )
    }

    @Test
    fun `no swipe outside Normal mode`() {
        tracker.down(1, 100f, 100f, T0)
        tracker.moveTo(1, 400f, 100f, T0 + 150)
        assertTrue(classify(mode = GestureMode.Transform).isEmpty())
    }

    // --- Discrete scroll --------------------------------------------------------

    @Test
    fun `vertical movement past the threshold is a discrete scroll`() {
        tracker.down(1, 100f, 100f, T0)
        tracker.moveTo(1, 105f, 350f, T0 + 150)
        tracker.up(1, 105f, 350f, T0 + 180)
        assertEquals(listOf(GestureEvent.VerticalScroll(250f)), classify())
    }

    @Test
    fun `smooth scroll setting suppresses the discrete scroll event`() {
        tracker.down(1, 100f, 100f, T0)
        tracker.up(1, 105f, 350f, T0 + 180)
        val flags = GestureFlags(smoothScroll = true, continuousZoom = false)
        assertTrue(classify(flags = flags).isEmpty())
    }

    // --- Discrete zoom ------------------------------------------------------------

    @Test
    fun `pinch past the ratio threshold is a discrete zoom`() {
        tracker.down(1, 0f, 0f, T0)
        tracker.down(2, 100f, 0f, T0 + 10)
        tracker.moveTo(2, 200f, 0f, T0 + 150)
        tracker.up(2, 200f, 0f, T0 + 180)
        tracker.up(1, 0f, 0f, T0 + 182)
        assertEquals(listOf(GestureEvent.PinchZoom(1f)), classify())
    }

    @Test
    fun `continuous zoom setting suppresses the discrete zoom event`() {
        tracker.down(1, 0f, 0f, T0)
        tracker.down(2, 100f, 0f, T0 + 10)
        tracker.moveTo(2, 200f, 0f, T0 + 150)
        val flags = GestureFlags(smoothScroll = false, continuousZoom = true)
        assertTrue(classify(flags = flags).isEmpty())
    }

    @Test
    fun `spreading fingers moving sideways co-fire zoom and swipe in dispatch order`() {
        tracker.down(1, 0f, 0f, T0)
        tracker.down(2, 100f, 0f, T0 + 10)
        tracker.moveTo(1, 300f, 10f, T0 + 150)
        tracker.moveTo(2, 700f, 5f, T0 + 150)
        tracker.up(1, 300f, 10f, T0 + 180)
        tracker.up(2, 700f, 5f, T0 + 182)
        val events = classify()
        assertEquals(2, events.size)
        assertTrue(events[0] is GestureEvent.PinchZoom)
        assertEquals(
            GestureEvent.Swipe(fingers = 2, direction = GestureEvent.Direction.Right),
            events[1]
        )
    }

    // --- Mode-entry predicates ------------------------------------------------------

    @Test
    fun `scroll entry needs one finger past the smooth threshold in Normal mode`() {
        tracker.down(1, 100f, 100f, T0)
        tracker.moveTo(1, 102f, 250f, T0 + 80)
        assertTrue(shouldEnterScroll(tracker, GestureMode.Normal, thresholds))
        assertFalse(shouldEnterScroll(tracker, GestureMode.Scroll, thresholds))
    }

    @Test
    fun `transform entry needs two fingers translating together`() {
        tracker.down(1, 100f, 100f, T0)
        tracker.down(2, 200f, 100f, T0 + 20)
        // Two fingers just resting do nothing.
        assertFalse(
            shouldEnterTransform(
                tracker,
                GestureMode.Normal,
                thresholds,
                continuousZoom = false
            )
        )
        // Both fingers slide right by 40 px (> 30 px pan-enter): centroid moves.
        tracker.moveTo(1, 140f, 100f, T0 + 60)
        tracker.moveTo(2, 240f, 100f, T0 + 60)
        assertTrue(
            shouldEnterTransform(
                tracker,
                GestureMode.Normal,
                thresholds,
                continuousZoom = false
            )
        )
        // Not while selecting.
        assertFalse(
            shouldEnterTransform(
                tracker,
                GestureMode.Selection,
                thresholds,
                continuousZoom = false
            )
        )
    }

    @Test
    fun `a second finger landing during a one-finger scroll upgrades to transform`() {
        // First finger lands and scrolls before the second one arrives.
        tracker.down(1, 100f, 100f, T0)
        tracker.moveTo(1, 100f, 250f, T0 + 80)
        assertFalse(
            shouldEnterTransform(tracker, GestureMode.Scroll, thresholds, continuousZoom = false)
        )
        tracker.down(2, 300f, 250f, T0 + 200)
        assertTrue(
            shouldEnterTransform(tracker, GestureMode.Scroll, thresholds, continuousZoom = false)
        )
    }

    @Test
    fun `a lopsided pinch that ends as a pan still zooms`() {
        // Finger 1 stays put, finger 2 spreads: the midpoint drifts like a pan.
        tracker.down(1, 0f, 0f, T0)
        tracker.down(2, 100f, 0f, T0 + 120)
        tracker.moveTo(2, 200f, 0f, T0 + 300)
        assertTrue(
            shouldEnterTransform(tracker, GestureMode.Normal, thresholds, continuousZoom = false)
        )
        assertEquals(GestureEvent.PinchZoom(1f), discretePinchZoom(tracker, noFlags))
    }

    @Test
    fun `a two-finger pan without spreading does not zoom`() {
        tracker.down(1, 0f, 0f, T0)
        tracker.down(2, 100f, 0f, T0 + 20)
        tracker.moveTo(1, 0f, 200f, T0 + 200)
        tracker.moveTo(2, 100f, 200f, T0 + 200)
        assertNull(discretePinchZoom(tracker, noFlags))
    }

    @Test
    fun `a staggered pinch after an early scroll still zooms out`() {
        tracker.down(1, 100f, 100f, T0)
        tracker.moveTo(1, 100f, 260f, T0 + 100) // scrolls before the second finger lands
        tracker.down(2, 500f, 260f, T0 + 250)
        tracker.moveTo(2, 300f, 260f, T0 + 400) // distance 400 -> 200
        assertEquals(GestureEvent.PinchZoom(-0.5f), discretePinchZoom(tracker, noFlags))
    }

    @Test
    fun `a small spread below the discrete threshold does not zoom`() {
        tracker.down(1, 0f, 0f, T0)
        tracker.down(2, 100f, 0f, T0 + 20)
        tracker.moveTo(2, 125f, 0f, T0 + 200) // ratio 0.25 < 0.3
        assertNull(discretePinchZoom(tracker, noFlags))
    }

    @Test
    fun `a pinch enters transform only when continuous zoom is on`() {
        tracker.down(1, 0f, 0f, T0)
        tracker.down(2, 100f, 0f, T0 + 10)
        tracker.moveTo(2, 120f, 0f, T0 + 80) // ratio 0.2 < 0.25, centroid travel 10 < 30
        assertFalse(
            shouldEnterTransform(
                tracker,
                GestureMode.Normal,
                thresholds,
                continuousZoom = true
            )
        )
        tracker.moveTo(2, 130f, 0f, T0 + 90) // ratio 0.3 > 0.25 (still centroid travel 15 < 30)
        assertTrue(
            shouldEnterTransform(
                tracker,
                GestureMode.Normal,
                thresholds,
                continuousZoom = true
            )
        )
        // With continuous zoom off, the same pinch does not engage: it's left
        // for the discrete snap-zoom at gesture end.
        assertFalse(
            shouldEnterTransform(
                tracker,
                GestureMode.Normal,
                thresholds,
                continuousZoom = false
            )
        )
    }

    // --- Infinite canvas: free pan --------------------------------------------------

    @Test
    fun `with free pan one finger enters transform in any direction`() {
        tracker.down(1, 100f, 100f, T0)
        tracker.moveTo(1, 120f, 100f, T0 + 40) // 20 px < 30 px pan-enter
        assertFalse(shouldEnterTransform(tracker, GestureMode.Normal, thresholds, false, freePan = true))
        tracker.moveTo(1, 140f, 100f, T0 + 60) // horizontal 40 px
        assertTrue(shouldEnterTransform(tracker, GestureMode.Normal, thresholds, false, freePan = true))
    }

    @Test
    fun `without free pan one finger never enters transform`() {
        tracker.down(1, 100f, 100f, T0)
        tracker.moveTo(1, 300f, 300f, T0 + 60)
        assertFalse(shouldEnterTransform(tracker, GestureMode.Normal, thresholds, false, freePan = false))
    }

    @Test
    fun `free pan leaves a held finger to selection`() {
        tracker.down(1, 100f, 100f, T0)
        tracker.moveTo(1, 105f, 100f, T0 + 40)
        assertFalse(shouldEnterTransform(tracker, GestureMode.Normal, thresholds, false, freePan = true))
        assertFalse(shouldEnterTransform(tracker, GestureMode.Selection, thresholds, false, freePan = true))
    }

    @Test
    fun `live zoom suppresses the discrete step at gesture end`() {
        tracker.down(1, 0f, 0f, T0)
        tracker.down(2, 100f, 0f, T0 + 10)
        tracker.moveTo(2, 200f, 0f, T0 + 150)
        assertNull(discretePinchZoom(tracker, GestureFlags(smoothScroll = true, continuousZoom = true)))
    }
}
