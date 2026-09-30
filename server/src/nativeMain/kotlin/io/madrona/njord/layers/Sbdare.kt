package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.geo.symbols.stringValues
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Seabed area
 *
 * Acronym: SBDARE
 *
 * Code: 121
 *
 * Follows the S-101 portrayal catalogue rule `SeabedArea.lua`:
 * - text of the nature-of-surface abbreviations (`S Sh`, `R`…), CHBLK;
 * - lines: solid CHGRD 0.32 mm, plus the text;
 * - areas that are always under water (WATLEV 3) or cover and uncover (WATLEV 4): dashed CHGRD
 *   boundary. A drying ledge of rock, lava or coral (WATLEV 4) gets the RCKLDG01 pattern instead
 *   of the text; any other area just gets the text.
 */
class Sbdare : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        val natsur = runCatching { feature.props.stringValues("NATSUR") }.getOrNull() ?: emptyList()
        val watlev = feature.props.intValue("WATLEV")
        val first = natsur.firstOrNull()?.toIntOrNull()
        if (watlev == 4 && first in DRYING_LEDGE) {
            feature.areaPattern(Sprite.RCKLDG01P)
        } else {
            seabedText(natsur)?.let { feature.props[SEABED_TEXT] = it.json }
        }
        if (natsur.isNotEmpty() && (watlev == 3 || watlev == 4)) {
            feature.props[DASHED_BOUNDARY] = true.json
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        Layer(
            id = "${key}_fill_pattern",
            type = LayerType.FILL,
            sourceLayer = sourceLayer,
            filter = listOf(Filters.all, Filters.eqTypePolyGon, listOf("has", "AP")).json,
            paint = Paint(fillPattern = listOf("get", "AP").json),
        ),
        lineLayerWithColor(
            theme = options.theme,
            color = Color.CHGRD,
            width = 1f,
            filter = Filters.eqTypeLineString,
        ),
        lineLayerWithColor(
            theme = options.theme,
            color = Color.CHGRD,
            width = 1f,
            style = LineStyle.CustomDash(12f, 6f),
            filter = listOf(Filters.all, Filters.eqTypePolyGon, listOf("==", DASHED_BOUNDARY, true)).json,
        ),
        Layer(
            id = "${key}_label",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            filter = listOf("has", SEABED_TEXT).json,
            layout = Layout(
                textFont = listOf(Font.ROBOTO_BOLD),
                textField = listOf("get", SEABED_TEXT).json,
                textSize = 12f,
                textOptional = true,
                symbolPlacement = Placement.POINT,
            ),
            paint = Paint(
                textColor = colorFrom(Color.CHBLK, options.theme).json,
                textHaloColor = colorFrom(Color.CHWHT, options.theme),
                textHaloWidth = 1.5f
            )
        ),
    )

    companion object {
        const val SEABED_TEXT = "_SB"
        const val DASHED_BOUNDARY = "_SBD"

        /** Rock, lava and coral: drawn as a drying ledge when they cover and uncover. */
        private val DRYING_LEDGE = setOf(9, 11, 14)

        /** S-101 abbreviations. Lava and boulders read as rock. */
        private val surface = mapOf(
            1 to "M", 2 to "Cy", 3 to "Si", 4 to "S", 5 to "St", 6 to "G", 7 to "P", 8 to "Cb",
            9 to "R", 11 to "R", 14 to "Co", 17 to "Sh", 18 to "R",
        )

        /**
         * Abbreviations joined by a space, unknown codes skipped. Null when nothing is left.
         * Some producers (SHOM) encode layered surfaces as `4/9`, sand over rock: kept as `S/R`.
         */
        fun seabedText(natsur: List<String>): String? =
            natsur.mapNotNull { value ->
                value.split('/')
                    .mapNotNull { it.trim().toIntOrNull()?.let(surface::get) }
                    .joinToString("/")
                    .takeIf { it.isNotEmpty() }
            }.joinToString(" ").takeIf { it.isNotEmpty() }
    }
}
