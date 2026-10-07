# Page layers

Each page has a stack of layers. Open the layers panel with the layers button in the toolbar,
or with **Layers** in the toolbar menu.

- **Tap a layer's name** to make it the active layer (shown inverted). New pen strokes, pasted
  content and inserted images go to the active layer. The eraser, scribble-to-erase, the lasso
  and **Clean all strokes** act only on the active layer.
- **Eye** shows or hides a layer. Hidden layers are not drawn and are left out of PDF, PNG and
  JPEG exports. Nothing on a hidden active layer can be erased or selected.
- **Arrows** move a layer up or down the stack. Higher layers are drawn over lower ones; within
  a layer, images are drawn under strokes.
- **Pencil** renames a layer.
- **Trash** deletes a layer and everything on it. Undo brings both back. The last layer cannot
  be deleted.
- **Add layer** adds an empty layer on top and makes it active.

Inserting space with the page cut moves content on every layer.

## Storage

- `Page.layers` holds the layer list as JSON, bottom layer first:
  `[{"id":0,"name":"Layer 1"},{"id":3,"name":"Ink","visible":false}]`. A page with only the
  default layer stores null, so pages created before layers existed are unchanged.
- `Stroke.layer` and `Image.layer` hold the layer id (default 0). Content whose layer is not in
  the list is shown and edited in the bottom layer.
- Database version 38 adds these columns (auto-migration from 37).
- Sync carries the page's layer list and the layer of each stroke and image. Older app versions
  ignore the fields and see a single layer.
- Xournal++ export writes one named `<layer>` per page layer, hidden ones included (the format
  has no visibility flag); import turns each `<layer>` into a page layer.

Code: `data/model/PageLayer.kt` (layer list operations), `PageDataManager` (state of the open
page), `editor/ui/toolbar/LayersPanel.kt` (panel).
