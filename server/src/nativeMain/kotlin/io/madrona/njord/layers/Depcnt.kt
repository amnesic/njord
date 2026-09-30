package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.model.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * DEPCNT, Depth Contour
 * Geometric primitives: LineString
 *
 * Set Attribute_A: 	(!)VALDCO; VERDAT;
 * Set Attribute_B: 	INFORM; NINFOM; NTXTDS; SCAMAX; SCAMIN; TXTDSC;
 * Set Attribute_C: 	RECDAT; RECIND; SORDAT; SORIND;
 *
 * Definition:
 * A line connecting points of equal water depth which is sometimes significantly displaced outside of soundings, symbols and other chart detail for clarity as well as generalization. Depth contours, therefore, often represent an approximate location of the line of equal depth as related to the surveyed line delineated on the source. Also referred to as depth curve. (IHO Dictionary, S-32, 5th Edition, 1314, 1315)
 * References
 * INT 1:	II 15, 30, 31;
 * S-4:	404.2; 410; 411, 411.2; 413-413.2;
 * Remarks:
 * Drying contours are encoded with negative values.
 * Distinction:
 * sounding; depth area; coastline;
 */
class Depcnt : Layerable() {
    override fun layers(options: LayerableOptions): Sequence<Layer> = sequenceOf(
        Layer(
            id = "${key}_line",
            type = LayerType.LINE,
            sourceLayer = sourceLayer,
            filter = Filters.eqTypeLineString,
            layout = Layout(lineJoin = LineJoin.ROUND, lineCap = LineCap.ROUND),
            paint = Paint(
                lineColor = colorFrom(Color.DEPCN, options.theme).json,
                lineWidth = 0.5f
            )
        ),
        Layer(
            id = "${key}_label",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            filter = listOf(Filters.all, Filters.eqTypeLineString, listOf("has", "VALDCO")).json,
            layout = Layout(
                textFont = listOf(Font.ROBOTO_BOLD),
                textField = contourValue(options.depth),
                textSize = 12f,
                symbolPlacement = Placement.LINE,
            ),
            paint = Paint(
                textColor = colorFrom(Color.DEPCN, options.theme).json,
                textHaloColor = colorFrom(Color.DEPDW, options.theme),
                textHaloWidth = 2f
            )
        ),
    )

    /**
     * VALDCO is always metres. Converted in the style rather than pre-encoded in the tile, so the
     * same tiles serve the three depth units. Drying contours are negative and keep their sign.
     * Feet are whole: `number-format` would group 1000 ft contours as `1,000`.
     */
    private fun contourValue(depth: Depth): JsonElement = when (depth) {
        Depth.METERS -> listOf("number-format", listOf("get", "VALDCO"), upToOneFractionDigit)
        Depth.FATHOMS -> listOf(
            "number-format",
            listOf("*", listOf("get", "VALDCO"), FATHOMS_PER_METER),
            upToOneFractionDigit,
        )
        Depth.FEET -> listOf(
            "to-string",
            listOf("round", listOf("*", listOf("get", "VALDCO"), FEET_PER_METER)),
        )
    }.json

    companion object {
        private const val FEET_PER_METER = 3.28084
        private const val FATHOMS_PER_METER = 0.546807

        /** Locale pinned so the decimal separator doesn't follow the viewer's browser locale. */
        private val upToOneFractionDigit = JsonObject(
            mapOf(
                "locale" to JsonPrimitive("en-US"),
                "max-fraction-digits" to JsonPrimitive(1),
            )
        )
    }
}