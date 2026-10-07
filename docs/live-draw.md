# Live draw over adb

Draws strokes from a JSON file onto the open page, as if written with the pen. Strokes
appear progressively, are saved like normal pen strokes, and can be undone.

Useful for scripted drawings, tests, and demos from a computer connected over adb.

## Usage

Open a page in Notable, push a stroke file, then send the broadcast:

```bash
adb push strokes.json /sdcard/Download/strokes.json
adb shell am broadcast -a com.ethran.notable.LIVE_DRAW -p com.ethran.notable \
    --es file /sdcard/Download/strokes.json --el delayMs 0 --ef size 1.6 --ei batch 80
```

Stop a running drawing:

```bash
adb shell am broadcast -a com.ethran.notable.LIVE_DRAW_STOP -p com.ethran.notable
```

| Extra | Type | Default | Meaning |
|---|---|---|---|
| `file` | string | required | Path to the stroke file, readable by the app |
| `delayMs` | long | 20 | Pause after each batch, in milliseconds |
| `size` | float | 1.6 | Ballpoint pen width |
| `color` | int | black | ARGB color for strokes without their own color |
| `batch` | int | 1 | Strokes drawn per screen refresh |
| `layer` | string | active layer | Layer for strokes without their own layer |

The screen refresh dominates the cost, so larger batches draw much faster at the price of
coarser animation. On a Nova Air C, 15,000 hatching strokes take about 30 s with
`--el delayMs 0 --ei batch 80`.

## Stroke file

A JSON array of strokes in page coordinates (pixels at zoom 1, origin top left). Each stroke
is either a point array or an object with its own color and layer:

```json
[
  [[100, 100], [400, 100]],
  {"color": "#C0392B", "layer": "Shields", "points": [[100, 200], [400, 300], [700, 200]]}
]
```

- Colors are `#RRGGBB` or `#AARRGGBB`.
- Layers are matched by name, ignoring case. A layer the page does not have yet is added on
  top, in the order the file first uses it, so a drawing can be split into layers that are
  then hidden or reordered in the layers panel (see [layers](layers.md)).
- Values after x and y in a point are ignored.
- Long segments are filled with intermediate points, so two-point lines render as lines.
- Strokes with fewer than two points are skipped.

## Security

The receiver is registered only while a page is open and requires
`android.permission.DUMP`, which the adb shell holds and third-party apps cannot. Other apps
on the device cannot draw through it.

Code: `editor/canvas/LiveDraw.kt` (receiver) and `editor/canvas/LiveDrawStrokes.kt`
(parsing, batching).
