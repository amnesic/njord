package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.layers.attributehelpers.Restrn
import io.madrona.njord.layers.attributehelpers.Restrn.Companion.restrn
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line, Area
 *
 * Object: Territorial sea area
 *
 * Acronym: TESARE
 *
 * Code: 135
 *
 * S-101 `TerritorialSeaArea.lua`: dashed CHGRF 0.64 mm, no fill, plus RESTRN01. RESTRN01 is
 * reduced here to what [Resare] already does: the entry or anchoring restriction symbol at the
 * centre of the area.
 */
class Tesare : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        val restrictions = feature.restrn()
        when {
            restrictions.any { it == Restrn.ENTRY_RESTRICTED || it == Restrn.ENTRY_PROHIBITED } ->
                feature.pointSymbol(Sprite.ENTRES51)
            restrictions.any { it == Restrn.ANCHORING_RESTRICTED || it == Restrn.ANCHORING_PROHIBITED } ->
                feature.pointSymbol(Sprite.ACHRES51)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(
            theme = options.theme,
            color = Color.CHGRF,
            width = 2f,
            style = LineStyle.CustomDash(6f, 3f),
        ),
        Layer(
            id = "${key}_area_point",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            filter = listOf(Filters.all, Filters.eqTypePolyGon, listOf("has", "SY")).json,
            layout = Layout(
                symbolPlacement = Placement.POINT,
                iconImage = listOf("get", "SY").json,
                iconAnchor = Anchor.CENTER,
                iconAllowOverlap = true,
            ),
        ),
    )
}
