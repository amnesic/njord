package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.Colour
import io.madrona.njord.geo.symbols.Colour.Companion.colors
import io.madrona.njord.layers.attributehelpers.Boyshp
import io.madrona.njord.layers.attributehelpers.Boyshp.Companion.boyshp
import io.madrona.njord.layers.attributehelpers.Catcam
import io.madrona.njord.layers.attributehelpers.Catcam.Companion.catcam
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Sprite

/**
 * Geometry Primitives: Point
 *
 * Object: Buoy, cardinal
 *
 * Acronym: BOYCAR
 *
 * Code: 14
 *
 * S-101 Traditional symbology: selects buoy body symbol based on shape (BOYSHP)
 * and colour bands (COLOUR), with fallback to simplified symbols (BOYCAR01-04).
 */
class Boycar : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        val shape = feature.boyshp()
        val colors = feature.colors()
        val catcam = feature.catcam()

        val symbol = when (shape) {
            Boyshp.PILLAR -> pillarSymbol(colors, catcam)
            Boyshp.SPAR -> sparSymbol(colors, catcam)
            Boyshp.CONICAL -> Sprite.BOYCON01
            Boyshp.CAN -> Sprite.BOYCAN01
            Boyshp.SPHERICAL -> Sprite.BOYSPH01
            Boyshp.BARREL -> Sprite.BOYBAR01
            Boyshp.SUPERBUOY -> Sprite.BOYSUP02
            // Default: use shape-specific symbol for pillar (most common for cardinal)
            // or fall back to simplified if no shape specified
            null, Boyshp.ICEBUOY -> pillarSymbol(colors, catcam)
        }

        feature.pointSymbol(symbol)
    }

    /**
     * Select pillar buoy symbol based on colour bands.
     * S-101 CardinalBuoy.lua colour matching:
     *   [Black, Yellow]              → BOYPIL22 (North)
     *   [Black, Yellow, Black]       → BOYPIL23 (East/South)
     *   [Yellow, Black]              → BOYPIL24 (South)
     *   [Yellow, Black, Yellow]      → BOYPIL25 (West)
     */
    private fun pillarSymbol(colors: List<Colour>, catcam: Catcam?): Sprite {
        return colorMatchedSymbol(
            colors = colors,
            catcam = catcam,
            byBlk = Sprite.BOYPIL22,
            byBlkByBlk = Sprite.BOYPIL23,
            ylBlk = Sprite.BOYPIL24,
            ylBlkYl = Sprite.BOYPIL25,
            fallback = Sprite.BOYPIL01
        )
    }

    /**
     * Select spar buoy symbol based on colour bands.
     */
    private fun sparSymbol(colors: List<Colour>, catcam: Catcam?): Sprite {
        return colorMatchedSymbol(
            colors = colors,
            catcam = catcam,
            byBlk = Sprite.BOYSPR22,
            byBlkByBlk = Sprite.BOYSPR23,
            ylBlk = Sprite.BOYSPR24,
            ylBlkYl = Sprite.BOYSPR25,
            fallback = Sprite.BOYSPR01
        )
    }

    /**
     * Match colour bands to cardinal buoy symbol variants.
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
