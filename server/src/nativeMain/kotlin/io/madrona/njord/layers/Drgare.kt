package io.madrona.njord.layers

import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.geo.symbols.floatValue
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Dredged area
 *
 * Acronym: DRGARE
 *
 * Code: 46
 *
 * S-52 symbolization: depth-based fill color (like DEPARE) + AP(DRGARE01) dot pattern overlay
 * + dashed boundary line in CHGRD.
 */
class Drgare(
    private val config: ChartsConfig = Singletons.config
) : Layerable() {

    private val areaFillColors = setOf(
        Color.DEPIT,
        Color.DEPVS,
        Color.DEPMS,
        Color.DEPMD,
        Color.DEPDW,
    )

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            areaLayerWithFillColor(theme = options.theme, options = areaFillColors),
            areaLayerWithFillPattern(Sprite.DRGARE01P),
            lineLayerWithColor(
                theme = options.theme,
                color = Color.CHGRD,
                style = LineStyle.DashLine,
                width = 0.5f
            ),
        )
    }

    override suspend fun preTileEncode(feature: ChartFeature) {
        var ac = Color.DEPMD
        feature.props.floatValue("DRVAL1")?.let { shallowRange ->
            val deepRange = feature.props.floatValue("DRVAL2") ?: shallowRange
            ac = when {
                shallowRange < 0.0f && deepRange <= 0.0f -> Color.DEPIT
                shallowRange <= config.shallowDepth -> Color.DEPVS
                shallowRange <= config.safetyDepth -> Color.DEPMS
                shallowRange <= config.deepDepth -> Color.DEPMD
                shallowRange > config.deepDepth -> Color.DEPDW
                else -> Color.DEPMD
            }
        }
        feature.areaColor(ac)
        feature.areaPattern(Sprite.DRGARE01P)
        feature.lineColor(Color.CHGRD)
    }
}
