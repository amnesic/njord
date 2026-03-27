package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.Colour
import io.madrona.njord.geo.symbols.Colour.Companion.colors
import io.madrona.njord.layers.attributehelpers.Boyshp
import io.madrona.njord.layers.attributehelpers.Boyshp.Companion.boyshp
import io.madrona.njord.layers.attributehelpers.Catlam
import io.madrona.njord.layers.attributehelpers.Catlam.Companion.catlam
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point
 *
 * Object: Buoy, lateral
 *
 * Acronym: BOYLAT
 *
 * Code: 17
 *
 * S-101 Traditional symbology: selects buoy symbol based on shape (BOYSHP)
 * and colour (COLOUR), matching the LateralBuoy.lua portrayal rule.
 */
open class Boylat : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        val shape = feature.boyshp()
        val colors = feature.colors()

        val symbol = when (shape) {
            Boyshp.CONICAL -> conicalSymbol(colors)
            Boyshp.CAN -> canSymbol(colors)
            Boyshp.SPHERICAL -> sphericalSymbol(colors)
            Boyshp.PILLAR -> pillarSymbol(colors)
            Boyshp.SPAR -> sparSymbol(colors)
            Boyshp.BARREL -> barrelSymbol(colors)
            Boyshp.SUPERBUOY -> Sprite.BOYSUP01
            Boyshp.ICEBUOY -> Sprite.BOYSPR01
            null -> fallbackByCatlam(feature.catlam(), colors)
        }

        feature.pointSymbol(symbol)
    }

    /**
     * When no BOYSHP, fall back to CATLAM to infer shape:
     * Port = can shape, Starboard = conical shape
     */
    private fun fallbackByCatlam(catlam: Catlam?, colors: List<Colour>): Sprite {
        return when (catlam) {
            Catlam.PORT_HAND_LATERAL_MARK -> canSymbol(colors)
            Catlam.STARBOARD_HAND_LATERAL_MARK -> conicalSymbol(colors)
            Catlam.PREFERRED_CHANNEL_TO_STARBOARD_LATERAL_MARK -> canSymbol(colors)
            Catlam.PREFERRED_CHANNEL_TO_PORT_LATERAL_MARK -> conicalSymbol(colors)
            null -> Sprite.BOYGEN03
        }
    }

    /**
     * Conical buoy: BOYCON + colour variant
     */
    private fun conicalSymbol(colors: List<Colour>): Sprite {
        return colorSymbol(
            colors = colors,
            red = Sprite.BOYCON10,
            green = Sprite.BOYCON11,
            yellow = Sprite.BOYCON12,
            rgr = Sprite.BOYCON30,
            grg = Sprite.BOYCON31,
            fallback = Sprite.BOYCON01
        )
    }

    /**
     * Can buoy: BOYCAN + colour variant
     */
    private fun canSymbol(colors: List<Colour>): Sprite {
        return colorSymbol(
            colors = colors,
            red = Sprite.BOYCAN10,
            green = Sprite.BOYCAN11,
            yellow = Sprite.BOYCAN12,
            rgr = Sprite.BOYCAN30,
            grg = Sprite.BOYCAN31,
            fallback = Sprite.BOYCAN01
        )
    }

    /**
     * Spherical buoy: BOYSPH + colour variant
     */
    private fun sphericalSymbol(colors: List<Colour>): Sprite {
        return when {
            colors.matchesRGR() -> Sprite.BOYSPH10
            colors.matchesGRG() -> Sprite.BOYSPH11
            colors.firstOrNull() == Colour.Red -> Sprite.BOYSPH10
            colors.firstOrNull() == Colour.Green -> Sprite.BOYSPH11
            colors.firstOrNull() == Colour.Yellow -> Sprite.BOYSPH12
            else -> Sprite.BOYSPH01
        }
    }

    /**
     * Pillar buoy: BOYPIL + colour variant
     */
    private fun pillarSymbol(colors: List<Colour>): Sprite {
        return colorSymbol(
            colors = colors,
            red = Sprite.BOYPIL10,
            green = Sprite.BOYPIL11,
            yellow = Sprite.BOYPIL12,
            rgr = Sprite.BOYPIL30,
            grg = Sprite.BOYPIL31,
            fallback = Sprite.BOYPIL01
        )
    }

    /**
     * Spar buoy: BOYSPR + colour variant
     */
    private fun sparSymbol(colors: List<Colour>): Sprite {
        return colorSymbol(
            colors = colors,
            red = Sprite.BOYSPR10,
            green = Sprite.BOYSPR11,
            yellow = Sprite.BOYSPR12,
            rgr = Sprite.BOYSPR30,
            grg = Sprite.BOYSPR31,
            fallback = Sprite.BOYSPR01
        )
    }

    /**
     * Barrel buoy: BOYBAR + colour variant
     */
    private fun barrelSymbol(colors: List<Colour>): Sprite {
        return when (colors.firstOrNull()) {
            Colour.Red -> Sprite.BOYBAR10
            Colour.Green -> Sprite.BOYBAR11
            Colour.Yellow -> Sprite.BOYBAR12
            else -> Sprite.BOYBAR01
        }
    }

    /**
     * Generic colour matching for shapes with RGR/GRG/yellow variants.
     * S-101 checks 3-colour patterns first, then single colour.
     * Suffix convention: 10=red, 11=green, 12=yellow, 30=RGR, 31=GRG
     */
    private fun colorSymbol(
        colors: List<Colour>,
        red: Sprite,
        green: Sprite,
        yellow: Sprite? = null,
        rgr: Sprite,
        grg: Sprite,
        fallback: Sprite
    ): Sprite {
        return when {
            colors.matchesRGR() -> rgr
            colors.matchesGRG() -> grg
            colors.firstOrNull() == Colour.Red -> red
            colors.firstOrNull() == Colour.Green -> green
            colors.firstOrNull() == Colour.Yellow && yellow != null -> yellow
            else -> fallback
        }
    }

    private fun List<Colour>.matchesRGR(): Boolean =
        size >= 3 && this[0] == Colour.Red && this[1] == Colour.Green && this[2] == Colour.Red

    private fun List<Colour>.matchesGRG(): Boolean =
        size >= 3 && this[0] == Colour.Green && this[1] == Colour.Red && this[2] == Colour.Green

    /**
     * Select symbol based on buoy shape and colour.
     * Used by subclasses (Boyspp) as fallback when no specific category matches.
     */
    fun symbolByShapeAndColor(feature: ChartFeature) {
        val shape = feature.boyshp()
        val colors = feature.colors()

        val symbol = when (shape) {
            Boyshp.CONICAL -> conicalSymbol(colors)
            Boyshp.CAN -> canSymbol(colors)
            Boyshp.SPHERICAL -> sphericalSymbol(colors)
            Boyshp.PILLAR -> pillarSymbol(colors)
            Boyshp.SPAR -> sparSymbol(colors)
            Boyshp.BARREL -> barrelSymbol(colors)
            Boyshp.SUPERBUOY -> Sprite.BOYSUP01
            Boyshp.ICEBUOY -> Sprite.BOYSPR01
            null -> Sprite.BOYGEN03
        }

        feature.pointSymbol(symbol)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol()
    )
}
