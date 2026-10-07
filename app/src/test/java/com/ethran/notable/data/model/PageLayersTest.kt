package com.ethran.notable.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PageLayersTest {

    private data class Item(val name: String, val layer: Int)

    private val three = listOf(
        PageLayer(id = 0, name = "Base"),
        PageLayer(id = 1, name = "Sketch"),
        PageLayer(id = 2, name = "Ink"),
    )

    @Test
    fun decode_missing_or_corrupt_gives_the_default_layer() {
        assertEquals(PageLayers.DEFAULT, PageLayers.decode(value = null))
        assertEquals(PageLayers.DEFAULT, PageLayers.decode(value = " "))
        assertEquals(PageLayers.DEFAULT, PageLayers.decode(value = "[]"))
        assertEquals(PageLayers.DEFAULT, PageLayers.decode(value = "{not json"))
    }

    @Test
    fun encode_and_decode_round_trip() {
        val layers = PageLayers.setVisible(layers = three, id = 1, visible = false)
        assertEquals(layers, PageLayers.decode(value = PageLayers.encode(layers = layers)))
    }

    @Test
    fun default_layers_are_stored_as_null() {
        assertNull(PageLayers.encode(layers = PageLayers.DEFAULT))
    }

    @Test
    fun decode_drops_duplicate_ids() {
        val decoded = PageLayers.decode(
            value = """[{"id":0,"name":"A"},{"id":0,"name":"B"},{"id":1,"name":"C"}]"""
        )
        assertEquals(listOf(0, 1), decoded.map { it.id })
    }

    @Test
    fun add_puts_a_new_layer_on_top_with_an_unused_id() {
        val added = PageLayers.add(layers = three, usedIds = listOf(7))
        assertEquals(PageLayer(id = 8, name = "Layer 4"), added.last())
        assertEquals(4, added.size)
    }

    @Test
    fun next_name_skips_taken_names() {
        val layers = listOf(PageLayer(id = 0, name = "Layer 1"), PageLayer(id = 1, name = "Layer 3"))
        assertEquals("Layer 4", PageLayers.nextName(layers = layers))
    }

    @Test
    fun the_last_layer_cannot_be_removed() {
        assertSame(PageLayers.DEFAULT, PageLayers.remove(layers = PageLayers.DEFAULT, id = 0))
        assertEquals(listOf(0, 2), PageLayers.remove(layers = three, id = 1).map { it.id })
    }

    @Test
    fun move_reorders_and_clamps() {
        assertEquals(listOf(1, 0, 2), PageLayers.move(layers = three, id = 0, delta = 1).map { it.id })
        assertEquals(listOf(2, 0, 1), PageLayers.move(layers = three, id = 2, delta = -5).map { it.id })
        assertSame(three, PageLayers.move(layers = three, id = 2, delta = 1))
        assertSame(three, PageLayers.move(layers = three, id = 9, delta = 1))
    }

    @Test
    fun rename_trims_and_ignores_blank_names() {
        assertEquals("Shading", PageLayers.rename(layers = three, id = 1, name = "  Shading ")[1].name)
        assertSame(three, PageLayers.rename(layers = three, id = 1, name = "  "))
    }

    @Test
    fun find_by_name_ignores_case() {
        assertEquals(1, PageLayers.findByName(layers = three, name = "sketch ")?.id)
        assertNull(PageLayers.findByName(layers = three, name = "missing"))
    }

    @Test
    fun resolve_maps_unknown_layers_to_the_bottom_layer() {
        val reordered = PageLayers.move(layers = three, id = 2, delta = -2)
        assertEquals(1, PageLayers.resolve(layers = reordered, layerId = 1))
        assertEquals(2, PageLayers.resolve(layers = reordered, layerId = 42))
    }

    @Test
    fun draw_order_follows_layers_and_skips_hidden_ones() {
        val items = listOf(Item("ink", 2), Item("base", 0), Item("sketch", 1), Item("orphan", 9), Item("ink2", 2))
        assertEquals(
            listOf("base", "orphan", "sketch", "ink", "ink2"),
            PageLayers.visibleInDrawOrder(items = items, layers = three) { it.layer }.map { it.name }
        )
        val hidden = PageLayers.setVisible(layers = three, id = 0, visible = false)
        assertEquals(
            listOf("sketch", "ink", "ink2"),
            PageLayers.visibleInDrawOrder(items = items, layers = hidden) { it.layer }.map { it.name }
        )
    }

    @Test
    fun by_visible_layer_returns_one_bucket_per_visible_layer() {
        val items = listOf(Item("a", 1), Item("b", 0))
        val hidden = PageLayers.setVisible(layers = three, id = 2, visible = false)
        assertEquals(
            listOf(listOf("b"), listOf("a")),
            PageLayers.byVisibleLayer(items = items, layers = hidden) { it.layer }.map { b -> b.map { it.name } }
        )
    }

    @Test
    fun a_single_layer_page_keeps_its_list() {
        val items = listOf(Item("a", 0), Item("b", 5))
        assertSame(items, PageLayers.visibleInDrawOrder(items = items, layers = PageLayers.DEFAULT) { it.layer })
        assertSame(items, PageLayers.inLayer(items = items, layers = PageLayers.DEFAULT, layerId = 0) { it.layer })
        val hidden = PageLayers.setVisible(layers = PageLayers.DEFAULT, id = 0, visible = false)
        assertEquals(emptyList<Item>(), PageLayers.visibleInDrawOrder(items = items, layers = hidden) { it.layer })
    }

    @Test
    fun in_layer_includes_orphans_only_in_the_bottom_layer() {
        val items = listOf(Item("base", 0), Item("orphan", 9), Item("ink", 2))
        assertEquals(listOf("base", "orphan"), PageLayers.inLayer(items = items, layers = three, layerId = 0) { it.layer }.map { it.name })
        assertEquals(listOf("ink"), PageLayers.inLayer(items = items, layers = three, layerId = 2) { it.layer }.map { it.name })
    }

    @Test
    fun active_layer_falls_back_to_the_top_layer() {
        assertEquals(2, PageLayerState(pageId = "p", layers = three, activeLayerId = 5).activeLayer.id)
        assertEquals(1, PageLayerState(pageId = "p", layers = three, activeLayerId = 1).activeLayer.id)
    }
}
