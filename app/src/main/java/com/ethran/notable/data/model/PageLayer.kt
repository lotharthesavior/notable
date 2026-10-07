package com.ethran.notable.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One layer of a page. Strokes and images reference it by [id] (`Stroke.layer`, `Image.layer`);
 * the stacking order is the order of the page's layer list, bottom first.
 */
@Serializable
data class PageLayer(
    val id: Int,
    val name: String,
    val visible: Boolean = true,
)

/** The layers of the open page and the one new content goes to. */
data class PageLayerState(
    val pageId: String = "",
    val layers: List<PageLayer> = PageLayers.DEFAULT,
    val activeLayerId: Int = PageLayers.DEFAULT_LAYER_ID,
) {
    val activeLayer: PageLayer
        get() = layers.firstOrNull { it.id == activeLayerId } ?: layers.last()
}

/**
 * Pure operations on a page's layer list, stored as JSON in `Page.layers`.
 *
 * A page without stored layers (null column, every page created before layers existed) has the
 * single [DEFAULT] layer with id [DEFAULT_LAYER_ID], which is also the column default of
 * `Stroke.layer` and `Image.layer`, so old pages need no data migration. Content whose layer id
 * is not in the list (e.g. restored by undo after its layer was deleted) belongs to the bottom
 * layer, see [resolve].
 */
object PageLayers {
    const val DEFAULT_LAYER_ID = 0
    val DEFAULT = listOf(PageLayer(id = DEFAULT_LAYER_ID, name = "Layer 1"))

    private val json = Json { ignoreUnknownKeys = true }

    /** Decodes the `Page.layers` column; null, blank, empty or corrupt values give [DEFAULT]. */
    fun decode(value: String?): List<PageLayer> {
        if (value.isNullOrBlank()) return DEFAULT
        val layers = try {
            json.decodeFromString<List<PageLayer>>(value)
        } catch (_: Exception) {
            return DEFAULT
        }
        return layers.distinctBy { it.id }.ifEmpty { DEFAULT }
    }

    /** Encodes for the `Page.layers` column; [DEFAULT] is stored as null. */
    fun encode(layers: List<PageLayer>): String? =
        if (layers.isEmpty() || layers == DEFAULT) null else json.encodeToString(layers)

    /** The id of the layer that content tagged [layerId] is shown and edited in. */
    fun resolve(layers: List<PageLayer>, layerId: Int): Int =
        if (layers.any { it.id == layerId }) layerId else layers.first().id

    /**
     * Adds a layer on top. Its id is above every id in [layers] and in [usedIds] (the layer ids
     * still carried by the page's content), so content of a deleted layer never joins a new one.
     */
    fun add(layers: List<PageLayer>, usedIds: Collection<Int> = emptyList(), name: String? = null): List<PageLayer> {
        val id = (layers.map { it.id } + usedIds).maxOrNull()?.plus(1) ?: DEFAULT_LAYER_ID
        return layers + PageLayer(id = id, name = name ?: nextName(layers))
    }

    /** "Layer N" with the smallest N, counting from the layer count, that is not taken. */
    fun nextName(layers: List<PageLayer>): String {
        val names = layers.map { it.name }.toSet()
        var n = layers.size + 1
        while ("Layer $n" in names) n++
        return "Layer $n"
    }

    /** Removes a layer; the last remaining layer cannot be removed. */
    fun remove(layers: List<PageLayer>, id: Int): List<PageLayer> =
        if (layers.size <= 1) layers else layers.filterNot { it.id == id }.ifEmpty { layers }

    /** Moves a layer [delta] places up (positive, towards the top) or down (negative). */
    fun move(layers: List<PageLayer>, id: Int, delta: Int): List<PageLayer> {
        val from = layers.indexOfFirst { it.id == id }
        if (from < 0) return layers
        val to = (from + delta).coerceIn(0, layers.lastIndex)
        if (to == from) return layers
        return layers.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun setVisible(layers: List<PageLayer>, id: Int, visible: Boolean): List<PageLayer> =
        layers.map { if (it.id == id) it.copy(visible = visible) else it }

    fun rename(layers: List<PageLayer>, id: Int, name: String): List<PageLayer> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return layers
        return layers.map { if (it.id == id) it.copy(name = trimmed) else it }
    }

    /** Finds a layer by name, ignoring case and surrounding spaces. */
    fun findByName(layers: List<PageLayer>, name: String): PageLayer? =
        layers.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }

    /**
     * Items of visible layers, bottom layer first, keeping the list order within a layer. With a
     * single visible layer this is the input list itself, so plain pages pay nothing.
     */
    fun <T> visibleInDrawOrder(items: List<T>, layers: List<PageLayer>, layerOf: (T) -> Int): List<T> {
        if (layers.size == 1) return if (layers[0].visible) items else emptyList()
        return byVisibleLayer(items, layers, layerOf).flatten()
    }

    /**
     * Items split per visible layer, bottom layer first, keeping the list order within a layer.
     * Renderers draw each layer's images and then its strokes, so a layer covers the ones below.
     */
    fun <T> byVisibleLayer(items: List<T>, layers: List<PageLayer>, layerOf: (T) -> Int): List<List<T>> {
        if (layers.size == 1) return if (layers[0].visible) listOf(items) else emptyList()
        val rank = HashMap<Int, Int>(layers.size)
        layers.forEachIndexed { index, layer -> if (layer.visible) rank[layer.id] = index }
        val known = layers.mapTo(HashSet()) { it.id }
        val bottomRank = rank[layers.first().id]
        val buckets = Array(layers.size) { ArrayList<T>() }
        for (item in items) {
            val id = layerOf(item)
            val index = rank[id] ?: if (id !in known) bottomRank else null
            if (index != null) buckets[index].add(item)
        }
        return layers.indices.filter { layers[it].visible }.map { buckets[it] }
    }

    /** Items that belong to layer [layerId] once orphans are resolved, see [resolve]. */
    fun <T> inLayer(items: List<T>, layers: List<PageLayer>, layerId: Int, layerOf: (T) -> Int): List<T> {
        if (layers.size == 1) return items
        val known = layers.mapTo(HashSet()) { it.id }
        val bottom = layers.first().id
        return items.filter {
            val id = layerOf(it)
            id == layerId || (layerId == bottom && id !in known)
        }
    }
}
