package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.Colour
import io.madrona.njord.geo.symbols.Colour.Companion.colors
import io.madrona.njord.layers.attributehelpers.Bcnshp
import io.madrona.njord.layers.attributehelpers.Bcnshp.Companion.bcnshp
import io.madrona.njord.layers.attributehelpers.Catlam
import io.madrona.njord.layers.attributehelpers.Catlam.Companion.catlam
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Sprite

/**
 * Geometry Primitives: Point
 *
 * Object: Beacon, lateral
 *
 * Acronym: BCNLAT
 *
 * Code: 7
 *
 * S-101 Traditional symbology: selects beacon symbol based on shape (BCNSHP)
 * and colour (COLOUR), matching the LateralBeacon.lua portrayal rule.
 */
open class Bcnlat : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        val shape = feature.bcnshp()
        val colors = feature.colors()
        val catlam = feature.catlam()

        val symbol = when (shape) {
            Bcnshp.BEACON_TOWER -> towerSymbol(colors)
            Bcnshp.PILE_BEACON, Bcnshp.BUOYANT_BEACON -> genericSymbol(colors)
            Bcnshp.STAKE_POLE_PERCH_POST -> Sprite.BCNSTK02
            Bcnshp.WHITY -> {
                // S-101: Whity (perch) uses PRICKE symbols based on CATLAM
                when (catlam) {
                    Catlam.PORT_HAND_LATERAL_MARK -> Sprite.PRICKE03
                    Catlam.STARBOARD_HAND_LATERAL_MARK -> Sprite.PRICKE04
                    else -> Sprite.BCNSTK02
                }
            }
            Bcnshp.LATTICE_BEACON -> Sprite.BCNLTC01
            Bcnshp.CAIRN -> Sprite.CAIRNS01
            null -> towerSymbol(colors) // default to tower
        }

        feature.pointSymbol(symbol)
    }

    /**
     * Tower beacon: BCNTOW + colour variant
     * S-101 LateralBeacon.lua checks 3-colour patterns (RGR/GRG), then single colour.
     */
    private fun towerSymbol(colors: List<Colour>): Sprite {
        return when {
            colors.matchesRGR() -> Sprite.BCNTOW30
            colors.matchesGRG() -> Sprite.BCNTOW31
            colors.firstOrNull() == Colour.Red -> Sprite.BCNTOW10
            colors.firstOrNull() == Colour.Green -> Sprite.BCNTOW11
            colors.firstOrNull() == Colour.Yellow -> Sprite.BCNTOW12
            colors.firstOrNull() == Colour.Black -> Sprite.BCNTOW13
            else -> Sprite.BCNTOW01
        }
    }

    /**
     * Generic beacon (pile/buoyant): BCNGEN + colour variant
     */
    private fun genericSymbol(colors: List<Colour>): Sprite {
        return when {
            colors.matchesRGR() -> Sprite.BCNGEN30
            colors.matchesGRG() -> Sprite.BCNGEN31
            colors.firstOrNull() == Colour.Red -> Sprite.BCNGEN10
            colors.firstOrNull() == Colour.Green -> Sprite.BCNGEN11
            colors.firstOrNull() == Colour.Yellow -> Sprite.BCNGEN12
            colors.firstOrNull() == Colour.Black -> Sprite.BCNGEN13
            else -> Sprite.BCNGEN01
        }
    }

    private fun List<Colour>.matchesRGR(): Boolean =
        size >= 3 && this[0] == Colour.Red && this[1] == Colour.Green && this[2] == Colour.Red

    private fun List<Colour>.matchesGRG(): Boolean =
        size >= 3 && this[0] == Colour.Green && this[1] == Colour.Red && this[2] == Colour.Green

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
