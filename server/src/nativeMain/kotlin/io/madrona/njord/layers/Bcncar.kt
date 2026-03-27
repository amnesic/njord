package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.Colour
import io.madrona.njord.geo.symbols.Colour.Companion.colors
import io.madrona.njord.layers.attributehelpers.Bcnshp
import io.madrona.njord.layers.attributehelpers.Bcnshp.Companion.bcnshp
import io.madrona.njord.layers.attributehelpers.Catcam
import io.madrona.njord.layers.attributehelpers.Catcam.Companion.catcam
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point
 *
 * Object: Beacon, cardinal
 *
 * Acronym: BCNCAR
 *
 * Code: 5
 *
 * S-101 Traditional symbology: selects beacon symbol based on shape (BCNSHP)
 * and colour bands (COLOUR), with fallback to simplified symbols (BCNCAR01-04).
 */
class Bcncar : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        val shape = feature.bcnshp()
        val colors = feature.colors()
        val catcam = feature.catcam()

        val symbol = when (shape) {
            Bcnshp.BEACON_TOWER -> towerSymbol(colors, catcam)
            Bcnshp.PILE_BEACON, Bcnshp.BUOYANT_BEACON -> genericSymbol(colors, catcam)
            Bcnshp.STAKE_POLE_PERCH_POST -> Sprite.BCNSTK02
            Bcnshp.LATTICE_BEACON -> Sprite.BCNLTC01
            Bcnshp.CAIRN, Bcnshp.WHITY -> genericSymbol(colors, catcam)
            // Default: try tower (most common for cardinal beacons)
            null -> towerSymbol(colors, catcam)
        }

        feature.pointSymbol(symbol)
    }

    /**
     * Select tower beacon symbol based on colour bands.
     * S-101 CardinalBeacon.lua colour matching:
     *   [Black, Yellow]              → BCNTOW22
     *   [Black, Yellow, Black]       → BCNTOW23
     *   [Yellow, Black]              → BCNTOW24
     *   [Yellow, Black, Yellow]      → BCNTOW25
     */
    private fun towerSymbol(colors: List<Colour>, catcam: Catcam?): Sprite {
        return colorMatchedSymbol(
            colors = colors,
            catcam = catcam,
            byBlk = Sprite.BCNTOW22,
            byBlkByBlk = Sprite.BCNTOW23,
            ylBlk = Sprite.BCNTOW24,
            ylBlkYl = Sprite.BCNTOW25,
            fallback = Sprite.BCNTOW01
        )
    }

    /**
     * Select generic beacon symbol (pile/buoyant) based on colour bands.
     */
    private fun genericSymbol(colors: List<Colour>, catcam: Catcam?): Sprite {
        return colorMatchedSymbol(
            colors = colors,
            catcam = catcam,
            byBlk = Sprite.BCNGEN22,
            byBlkByBlk = Sprite.BCNGEN23,
            ylBlk = Sprite.BCNGEN24,
            ylBlkYl = Sprite.BCNGEN25,
            fallback = Sprite.BCNGEN01
        )
    }

    /**
     * Match colour bands to cardinal beacon symbol variants.
     * If no colour match, fall back by CATCAM direction, then to generic fallback.
     */
    private fun colorMatchedSymbol(
        colors: List<Colour>,
        catcam: Catcam?,
        byBlk: Sprite,      // Black-Yellow
        byBlkByBlk: Sprite, // Black-Yellow-Black
        ylBlk: Sprite,      // Yellow-Black
        ylBlkYl: Sprite,    // Yellow-Black-Yellow
        fallback: Sprite
    ): Sprite {
        return when {
            colors == listOf(Colour.Black, Colour.Yellow, Colour.Black) -> byBlkByBlk
            colors == listOf(Colour.Yellow, Colour.Black, Colour.Yellow) -> ylBlkYl
            colors == listOf(Colour.Black, Colour.Yellow) -> byBlk
            colors == listOf(Colour.Yellow, Colour.Black) -> ylBlk
            // No colour data — infer from cardinal direction
            else -> when (catcam) {
                Catcam.NORTH_CARDINAL_MARK -> byBlk
                Catcam.EAST_CARDINAL_MARK -> byBlkByBlk
                Catcam.SOUTH_CARDINAL_MARK -> ylBlk
                Catcam.WEST_CARDINAL_MARK -> ylBlkYl
                null -> fallback
            }
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
