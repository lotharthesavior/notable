# Infinite canvas, panning and zoom

How a page can be moved around and zoomed: the infinite canvas setting, the zoom levels, the
toolbar zoom control, and the finger gestures that drive them.

## Infinite canvas

**Settings → General → Infinite canvas** (off by default).

A bounded page has its top-left corner fixed at the page origin `(0, 0)`: you can scroll right
and down, never above or left of it. With the infinite canvas on, pages scroll in every direction,
so you can write above and to the left of where the page started. Page coordinates (stroke
points, image positions) can then be negative.

Only pages with a **native background** (blank, lined, dotted, squared, hexed) become infinite.
PDF and image backgrounds stay anchored at the origin, and cover-image pages cannot move at all.
The check is `PageDataManager.isInfiniteCanvasForCurrentPage()`.

| Piece | Where | What it does |
| :-- | :-- | :-- |
| Scroll floor | `editor/utils/CanvasBounds.kt` | `clampScroll` / `limitScrollDelta`: 0 on bounded pages, none on infinite ones |
| Pan and pinch | `editor/PageView.kt` | `updateScroll`, `simpleUpdateScroll`, `updateZoom` use `CanvasBounds` instead of clamping at 0 |
| Backgrounds | `editor/drawing/backgrounds.kt` | lined/dotted/squared patterns use floor modulo (`mod`), so they stay aligned at negative scroll |
| Export, thumbnails | `io/PageContentRenderer.kt`, `io/ExportEngine.kt` | render from the content origin (`computeContentOrigin`), so content at negative coordinates is not cut off |
| Scroll indicators | `editor/ui/ScrollIndicator.kt` | the range starts above/left of the origin when scrolled there |

The vertical scroll position is still persisted as an integer (`Page.scroll`), and may now be
negative. The horizontal position is not persisted, as before.

## Zoom levels

Zoom is clamped to **0.5× – 2×** (`MIN_ZOOM` / `MAX_ZOOM` in `gestures/GestureThresholds.kt`):
half to double the original size.

- **Stepped zoom** moves one step through `DISCRETE_ZOOM_STEPS` = 0.5×, 1×, 2×
  (`nextDiscreteZoom`). A level between steps (for example one left by a live pinch) moves to the
  nearest step in the requested direction.
- **Live zoom** follows the fingers continuously within the same bounds, snapping to 1× or the
  screen-fit ratio when close.
  It is on when **Continuous Zoom** or **Infinite canvas** is enabled
  (`AppSettings.effectiveContinuousZoom`).

## Toolbar zoom control

A placeable toolbar element, `ZOOM` (`CustomKind.ZOOM`), shown as **[−] 100% [+]**. It is in the
default pinned zone and can be moved or hidden in **Settings → Toolbar**.

- **−** / **+** step through 0.5×, 1×, 2× (`ToolbarAction.StepZoom` → `CanvasCommand.StepZoom` →
  `EditorControlTower.stepZoom`).
- Tapping the percentage resets zoom and horizontal scroll (`ToolbarAction.ResetView`).
- Hidden on pages that cannot be zoomed (`ToolbarUiState.isZoomAllowed`).

## Finger gestures

Recognition lives in `gestures/`: `PointerTracker` holds the finger geometry,
`GestureClassifier` decides modes and end-of-gesture events, and `EditorGestureReceiver` applies
them. With the infinite canvas on, panning and zooming are one continuous movement:

- **One finger** past the pan threshold (30 dp) enters `Transform` and pans in any direction
  (`shouldEnterTransform(…, freePan = true)`). The vertical-only `Scroll` mode is skipped.
- **A second finger** can join at any time: the pinch pair forms on the two fingers down and zoom
  follows them live while the drag keeps panning.
- **Lifting a finger** keeps the gesture going: the remaining finger pans, and zoom pauses (a
  lifted finger's stale position never turns a pan into a zoom). A finger that joins again forms
  a new pinch pair and zooming resumes, without restarting the gesture
  (`PointerTracker.refreshPinchPair`).

Without the infinite canvas (and with Continuous Zoom off) the stepped zoom applies at gesture end:

- The pinch is measured from where both fingers were when the second one landed, so the fingers
  need not touch down together.
- A one-finger scroll upgrades to a two-finger transform when a second finger lands.
- A pinch that ends as a pan (its midpoint drifted because one finger moved more) still zooms
  (`discretePinchZoom`).
- The finger distance must change by 30% (`PINCH_ZOOM_THRESHOLD`).

Taps, hold-to-select and multi-finger swipes are unchanged. On infinite-canvas pages a one-finger
horizontal drag pans instead of turning the page.

## Page load redraw

When a page opens, `PageView.loadPage` draws the loaded strokes straight into the page bitmap.
It used to send a one-shot `forceUpdate` event that could fire before the canvas subscribed and
be dropped, leaving a stale saved preview on screen with only later-redrawn areas showing the
real strokes.

## Known gaps

- Xournal++ (`.xopp`) export does not shift content at negative coordinates, so it lands off the
  page in Xournal++.
- With **Preview PDF Pagination** on, content above the origin shows page labels such as
  "Subpage 0".
