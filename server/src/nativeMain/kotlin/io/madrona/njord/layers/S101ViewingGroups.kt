package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.model.Layer
import io.madrona.njord.model.LayerType
import io.madrona.njord.util.resourceAsString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

/**
 * S-101 viewing groups for each S-57 class, from `resources/s101/viewing_groups.json`, generated
 * by `s101/extract_viewing_groups.py` out of the IHO S-101 feature and portrayal catalogues.
 *
 * Every style layer gets `metadata` naming the S-101 feature type, viewing group, viewing group
 * layer and display mode it belongs to, so a client can offer the S-101 Display Base / Standard /
 * Other modes, or its own selection, by toggling layers. Text-only layers also carry the text
 * viewing group, as S-101 lets the mariner switch names and other chart text separately.
 */
class S101ViewingGroups(
    json: String? = resourceAsString(RESOURCE),
) {
    private val catalogue: Catalogue? = json?.let { decoder.decodeFromString(Catalogue.serializer(), it) }

    /** Display modes, viewing group layers and catalogue versions, for the style's root metadata. */
    val styleMetadata: JsonElement? = catalogue?.let {
        JsonObject(
            mapOf(
                "s101:source" to decoder.encodeToJsonElement(it.source),
                "s101:displayModes" to decoder.encodeToJsonElement(it.displayModes),
                "s101:viewingGroupLayers" to decoder.encodeToJsonElement(it.viewingGroupLayers),
            )
        )
    }

    fun classFor(sourceLayer: String?): S101Class? = sourceLayer?.let { catalogue?.classes?.get(it.uppercase()) }

    fun metadataFor(layer: Layer): JsonElement? {
        val entry = classFor(layer.sourceLayer) ?: return null
        val values = mutableMapOf<String, JsonElement>(
            "s101:feature" to (entry.features.firstOrNull() ?: "").json,
            "s101:viewingGroup" to entry.viewingGroup.json,
            "s101:viewingGroupLayer" to (entry.viewingGroupLayer ?: "").json,
            "s101:displayMode" to (entry.displayMode ?: "").json,
        )
        if (layer.isTextOnly() && entry.textViewingGroup != null) {
            values["s101:textViewingGroup"] = entry.textViewingGroup.json
            values["s101:textViewingGroupLayer"] = (entry.textViewingGroupLayer ?: "").json
        }
        return JsonObject(values)
    }

    private fun Layer.isTextOnly() =
        type == LayerType.SYMBOL && layout?.let { it.textField != null && it.iconImage == null } == true

    @Serializable
    data class Catalogue(
        val source: JsonElement,
        val displayModes: List<DisplayMode>,
        val viewingGroupLayers: List<ViewingGroupLayer>,
        val classes: Map<String, S101Class>,
    )

    @Serializable
    data class DisplayMode(val id: String, val name: String, val viewingGroupLayers: List<String>)

    @Serializable
    data class ViewingGroupLayer(val id: String, val name: String, val viewingGroups: List<Int>)

    @Serializable
    data class S101Class(
        val features: List<String> = emptyList(),
        val viewingGroup: Int,
        val viewingGroups: List<Int> = emptyList(),
        val viewingGroupLayer: String? = null,
        val displayMode: String? = null,
        val textViewingGroup: Int? = null,
        val textViewingGroupLayer: String? = null,
        val note: String? = null,
    )

    companion object {
        const val RESOURCE = "s101/viewing_groups.json"
        private val decoder = Json { ignoreUnknownKeys = true }
    }
}
