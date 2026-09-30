package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.model.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Magnetic variation
 *
 * Acronym: MAGVAR
 *
 * Code: 81
 *
 * Follows the S-101 portrayal catalogue rule `MagneticVariation.lua`:
 * - points: MAGVAR01 and the value of VALMAG, CHBLK, offset down and right;
 * - lines: solid CHMGF 0.64 mm, MAGVAR51 and `varn %3.2f`;
 * - areas: MAGVAR51 only.
 * Everything is read from VALMAG in the style, so the tiles are unchanged.
 */
class Magvar : Layerable() {

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(
            theme = options.theme,
            color = Color.CHMGF,
            width = 2f,
            filter = Filters.eqTypeLineString,
        ),
        pointLayerFromSymbol(
            symbol = Symbol.Sprite(Sprite.MAGVAR01),
            anchor = Anchor.CENTER,
            filter = Filters.eqTypePoint,
        ),
        Layer(
            id = "${key}_line_symbol",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            filter = Filters.eqTypeLineString,
            layout = Layout(
                symbolPlacement = Placement.LINE_CENTER,
                iconImage = Sprite.MAGVAR51.name.json,
                iconAllowOverlap = true,
                iconRotationAlignment = IconRotationAlignment.VIEWPORT,
            ),
        ),
        areaLayerWithPointSymbol(symbol = Sprite.MAGVAR51),
        label(
            id = "${key}_point_label",
            filter = Filters.eqTypePoint,
            text = listOf("to-string", listOf("get", "VALMAG")).json,
            theme = options.theme,
        ),
        label(
            id = "${key}_line_label",
            filter = Filters.eqTypeLineString,
            text = listOf("concat", "varn ", listOf("number-format", listOf("get", "VALMAG"), twoDecimals)).json,
            theme = options.theme,
            placement = Placement.LINE_CENTER,
        ),
    )

    private fun label(
        id: String,
        filter: kotlinx.serialization.json.JsonElement,
        text: kotlinx.serialization.json.JsonElement,
        theme: Theme,
        placement: Placement = Placement.POINT,
    ) = Layer(
        id = id,
        type = LayerType.SYMBOL,
        sourceLayer = sourceLayer,
        filter = listOf(Filters.all, filter, listOf("has", "VALMAG")).json,
        layout = Layout(
            textFont = listOf(Font.ROBOTO_BOLD),
            textField = text,
            textSize = 12f,
            // S-101 LocalOffset 3.51,3.51 mm: down and to the right of the symbol
            textAnchor = Anchor.TOP_LEFT,
            textOffset = Offset.Coord(x = 1f, y = 1f).property,
            textOptional = true,
            symbolPlacement = placement,
        ),
        paint = Paint(
            textColor = colorFrom(Color.CHBLK, theme).json,
            textHaloColor = colorFrom(Color.CHWHT, theme),
            textHaloWidth = 1.5f
        )
    )

    companion object {
        /** `%3.2f`, locale pinned so the decimal separator doesn't follow the browser. */
        private val twoDecimals = JsonObject(
            mapOf(
                "locale" to JsonPrimitive("en-US"),
                "min-fraction-digits" to JsonPrimitive(2),
                "max-fraction-digits" to JsonPrimitive(2),
            )
        )
    }
}
