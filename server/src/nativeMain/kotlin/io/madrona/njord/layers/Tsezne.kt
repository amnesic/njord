package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Traffic Separation Zone
 *
 * Acronym: TSEZNE
 *
 * Code: 150
 */
class Tsezne : Layerable() {
    private val ac = Color.TRFCF
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.areaColor(ac)
        feature.lineColor(ac)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(ac, options.theme, opacity = 0.25f),
        lineLayerWithColor(theme = options.theme, width = 0.5f, options = setOf(ac), style = LineStyle.DashLine),
    )
}
