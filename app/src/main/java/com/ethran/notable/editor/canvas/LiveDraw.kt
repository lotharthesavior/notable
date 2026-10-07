package com.ethran.notable.editor.canvas

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import androidx.core.content.ContextCompat
import com.ethran.notable.editor.PageView
import com.ethran.notable.editor.utils.Pen
import com.ethran.notable.editor.utils.handleDraw
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
 *       --es file /sdcard/Download/strokes.json --el delayMs 20 --ef size 1.6
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
                    job?.cancel()
                    job = coroutineScope.launch(Dispatchers.Default) { draw(path, delayMs, size, color) }
                }
            }
        }
    }

    private suspend fun draw(path: String, delayMs: Long, size: Float, color: Int) {
        val strokes = try {
            parseLiveDrawStrokes(File(path).readText()).map { densifyStroke(it) }
        } catch (e: Exception) {
            log.e("LiveDraw: cannot read $path: ${e.message}")
            return
        }
        log.i("LiveDraw: drawing ${strokes.size} strokes from $path")
        for (points in strokes) {
            if (points.size < 2) continue
            // Same thread as pen strokes: strokeHistoryBatch is not thread-safe.
            withContext(Dispatchers.Main) {
                CanvasEventBus.drawingInProgress.withLock {
                    handleDraw(page, strokeHistoryBatch, size, color, Pen.BALLPEN, points)
                }
            }
            val pad = (size * 2).toInt() + 2
            drawCanvas.refreshManager.refreshUi(
                Rect(
                    points.minOf { it.x }.toInt() - pad,
                    points.minOf { it.y }.toInt() - pad,
                    points.maxOf { it.x }.toInt() + pad,
                    points.maxOf { it.y }.toInt() + pad
                )
            )
            CanvasEventBus.commitHistorySignal.emit(Unit)
            if (delayMs > 0) delay(delayMs)
        }
        log.i("LiveDraw: done")
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
