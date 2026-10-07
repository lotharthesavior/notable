package com.ethran.notable.editor.canvas

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.ethran.notable.data.db.MAX_PRESSURE_NORMALIZED
import com.ethran.notable.data.db.Stroke
import com.ethran.notable.data.db.StrokePoint
import com.ethran.notable.editor.PageView
import com.ethran.notable.editor.utils.Pen
import com.ethran.notable.editor.utils.calculateBoundingBox
import com.ethran.notable.editor.utils.strokeBounds
import io.shipbook.shipbooksdk.ShipBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Draws strokes from a JSON file onto the open page, one stroke at a time, through the same
 * path as a finished pen stroke, so they appear live and are saved with undo history.
 *
 *   adb shell am broadcast -a com.ethran.notable.LIVE_DRAW \
 *       --es file /sdcard/Download/strokes.json --el delayMs 20 --ef size 1.6 --ei batch 1
 *
 * JSON: [[[x, y], [x, y], ...], ...] in page coordinates (see parseLiveDrawStrokes).
 * Only the shell user can send it: the receiver requires android.permission.DUMP, which
 * third-party apps cannot hold.
 * Send com.ethran.notable.LIVE_DRAW_STOP to cancel a running drawing.
 */
class LiveDraw(
    private val drawCanvas: DrawCanvas,
    private val page: PageView,
    private val coroutineScope: CoroutineScope,
    private val strokeHistoryBatch: MutableList<String>,
) {
    private val log = ShipBook.getLogger("LiveDraw")
    private var job: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_STOP -> job?.cancel()
                ACTION_DRAW -> {
                    val path = intent.getStringExtra("file") ?: return
                    val delayMs = intent.getLongExtra("delayMs", 20L)
                    val size = intent.getFloatExtra("size", 1.6f)
                    val color = intent.getIntExtra("color", 0xFF000000.toInt())
                    val batch = intent.getIntExtra("batch", 1).coerceAtLeast(1)
                    job?.cancel()
                    job = coroutineScope.launch(Dispatchers.Default) {
                        draw(path, delayMs, size, color, batch)
                    }
                }
            }
        }
    }

    private suspend fun draw(path: String, delayMs: Long, size: Float, color: Int, batch: Int) {
        val strokes = try {
            parseLiveDrawStrokes(File(path).readText()).map { densifyStroke(it) }
        } catch (e: Exception) {
            log.e("LiveDraw: cannot read $path: ${e.message}")
            return
        }
        log.i("LiveDraw: drawing ${strokes.size} strokes from $path")
        // Each batch is drawn together and refreshed once; the screen refresh dominates the
        // cost, so larger batches draw faster at the price of coarser animation.
        for (group in strokes.filter { it.size >= 2 }.chunked(batch)) {
            val batchStrokes = group.map { toStroke(it, size, color) }
            val dirty = strokeBounds(batchStrokes)
            // Same thread as pen strokes: strokeHistoryBatch is not thread-safe. One
            // addStrokes/drawArea per batch instead of per stroke (see handleDraw).
            withContext(Dispatchers.Main) {
                CanvasEventBus.drawingInProgress.withLock {
                    page.addStrokes(batchStrokes)
                    page.drawAreaPageCoordinates(dirty)
                    strokeHistoryBatch.addAll(batchStrokes.map { it.id })
                }
            }
            drawCanvas.refreshManager.refreshUi(dirty)
            CanvasEventBus.commitHistorySignal.emit(Unit)
            if (delayMs > 0) delay(delayMs)
        }
        log.i("LiveDraw: done")
    }

    private fun toStroke(points: List<StrokePoint>, size: Float, color: Int): Stroke {
        val box = calculateBoundingBox(points) { Pair(it.x, it.y) }
        box.inset(-size, -size)
        return Stroke(
            size = size,
            pen = Pen.BALLPEN,
            pageId = page.currentPageId,
            top = box.top,
            bottom = box.bottom,
            left = box.left,
            right = box.right,
            points = points,
            color = color,
            maxPressure = MAX_PRESSURE_NORMALIZED
        )
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(ACTION_DRAW)
            addAction(ACTION_STOP)
        }
        current?.unregister()
        ContextCompat.registerReceiver(
            drawCanvas.context, receiver, filter, PERMISSION, null, ContextCompat.RECEIVER_EXPORTED
        )
        current = this
    }

    fun unregister() {
        job?.cancel()
        try {
            drawCanvas.context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
        if (current === this) current = null
    }

    companion object {
        const val ACTION_DRAW = "com.ethran.notable.LIVE_DRAW"
        const val ACTION_STOP = "com.ethran.notable.LIVE_DRAW_STOP"
        private const val PERMISSION = "android.permission.DUMP"
        private var current: LiveDraw? = null
    }
}
