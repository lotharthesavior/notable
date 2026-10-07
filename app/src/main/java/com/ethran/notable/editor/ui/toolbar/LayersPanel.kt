package com.ethran.notable.editor.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.ethran.notable.data.model.PageLayer
import com.ethran.notable.data.model.PageLayerState
import com.ethran.notable.editor.ToolbarAction
import com.ethran.notable.ui.components.ScaledPopup
import com.ethran.notable.ui.noRippleClickable
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check
import compose.icons.feathericons.ChevronDown
import compose.icons.feathericons.ChevronUp
import compose.icons.feathericons.Edit2
import compose.icons.feathericons.Eye
import compose.icons.feathericons.EyeOff
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Trash2

/**
 * Layers of the open page, top layer first. Tapping a name makes it the active layer: new
 * strokes and images go there, and the eraser and lasso act only on it.
 */
@Composable
fun LayersPanel(
    state: PageLayerState,
    onAction: (ToolbarAction) -> Unit,
) {
    val density = LocalDensity.current
    ScaledPopup(
        alignment = Alignment.TopEnd,
        onDismissRequest = { onAction(ToolbarAction.ToggleLayersPanel(false)) },
        offset = with(density) { IntOffset((-10).dp.roundToPx(), 50.dp.roundToPx()) },
        properties = PopupProperties(focusable = true),
    ) {
        LayersPanelContent(state = state, onAction = onAction)
    }
}

@Composable
private fun LayersPanelContent(
    state: PageLayerState,
    onAction: (ToolbarAction) -> Unit,
) {
    // Layer being renamed, if any; one at a time.
    var renaming by remember { mutableStateOf<Int?>(null) }
    val activeId = state.activeLayer.id
    val canDelete = state.layers.size > 1

    Column(
        Modifier
            .width(340.dp)
            .border(1.dp, Color.Black, RectangleShape)
            .background(Color.White)
    ) {
        Text(
            text = "Layers",
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black))

        Column(Modifier.verticalScroll(rememberScrollState())) {
            val topFirst = state.layers.asReversed()
            topFirst.forEachIndexed { index, layer ->
                LayerRow(
                    layer = layer,
                    isActive = layer.id == activeId,
                    isTop = index == 0,
                    isBottom = index == topFirst.lastIndex,
                    canDelete = canDelete,
                    isRenaming = renaming == layer.id,
                    onStartRename = { renaming = layer.id },
                    onFinishRename = { name ->
                        renaming = null
                        onAction(ToolbarAction.RenameLayer(layer.id, name))
                    },
                    onAction = onAction,
                )
                if (index != topFirst.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF777777)))
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .noRippleClickable { onAction(ToolbarAction.AddLayer) }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(FeatherIcons.Plus, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = "Add layer", color = Color.Black)
        }
    }
}

@Composable
private fun LayerRow(
    layer: PageLayer,
    isActive: Boolean,
    isTop: Boolean,
    isBottom: Boolean,
    canDelete: Boolean,
    isRenaming: Boolean,
    onStartRename: () -> Unit,
    onFinishRename: (String) -> Unit,
    onAction: (ToolbarAction) -> Unit,
) {
    // The active layer is shown inverted: on e-ink a solid bar reads better than a tint.
    val fg = if (isActive) Color.White else Color.Black
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(if (isActive) Color.Black else Color.White)
            .padding(horizontal = 4.dp)
    ) {
        PanelIcon(
            icon = if (layer.visible) FeatherIcons.Eye else FeatherIcons.EyeOff,
            description = if (layer.visible) "hide ${layer.name}" else "show ${layer.name}",
            tint = fg,
        ) { onAction(ToolbarAction.SetLayerVisible(layer.id, !layer.visible)) }

        if (isRenaming) {
            var text by remember { mutableStateOf(layer.name) }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.Black, fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onFinishRename(text) }),
                modifier = Modifier
                    .weight(1f)
                    .background(Color.White)
                    .border(1.dp, Color.Black)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
            PanelIcon(icon = FeatherIcons.Check, description = "save name", tint = fg) {
                onFinishRename(text)
            }
        } else {
            Text(
                text = layer.name,
                color = fg,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .noRippleClickable { onAction(ToolbarAction.SelectLayer(layer.id)) }
                    .padding(horizontal = 6.dp, vertical = 10.dp)
            )
            PanelIcon(icon = FeatherIcons.Edit2, description = "rename ${layer.name}", tint = fg) {
                onStartRename()
            }
        }
        PanelIcon(
            icon = FeatherIcons.ChevronUp,
            description = "move ${layer.name} up",
            tint = fg,
            enabled = !isTop,
        ) { onAction(ToolbarAction.MoveLayer(layer.id, 1)) }
        PanelIcon(
            icon = FeatherIcons.ChevronDown,
            description = "move ${layer.name} down",
            tint = fg,
            enabled = !isBottom,
        ) { onAction(ToolbarAction.MoveLayer(layer.id, -1)) }
        PanelIcon(
            icon = FeatherIcons.Trash2,
            description = "delete ${layer.name}",
            tint = fg,
            enabled = canDelete,
        ) { onAction(ToolbarAction.DeleteLayer(layer.id)) }
    }
}

@Composable
private fun PanelIcon(
    icon: ImageVector,
    description: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .noRippleClickable { if (enabled) onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) tint else Color(0xFF999999),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
@Preview(showBackground = true)
fun LayersPanelPreview() {
    LayersPanelContent(
        state = PageLayerState(
            pageId = "page",
            layers = listOf(
                PageLayer(id = 0, name = "Sky"),
                PageLayer(id = 1, name = "Horses", visible = false),
                PageLayer(id = 2, name = "Knights"),
            ),
            activeLayerId = 2,
        ),
        onAction = {},
    )
}
