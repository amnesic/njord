package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line, Area
 *
 * Object: Administration area (Named)
 *
 * Acronym: ADMARE
 *
 * Code: 1
 *
 * S-101 `AdministrationArea.lua`, plain boundaries: dashed CHGRF 0.64 mm, no fill.
 */
class Admare : Layerable() {

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(
            theme = options.theme,
            color = Color.CHGRF,
            width = 2f,
            style = LineStyle.CustomDash(6f, 3f),
        ),
    )
}
