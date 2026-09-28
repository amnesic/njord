package io.madrona.njord

import kotlinx.serialization.Serializable

@Serializable
data class RegionExportConfig(
    val name: String,
    val description: String,
    val coverage: String, // WKT polygon
)

@Serializable
data class ChartsConfig(
    val adminKey: String,
    val adminUser: String,
    val adminPass: String,
    val adminExpirationSeconds: Long,
    val pgConnectionInfo: String,
    val host: String,
    val port: Int,
    val consoleMetrics: Boolean,
    val chartTempData: String,
    val webStaticContent: String,
    val shallowDepth: Float,
    val safetyDepth: Float,
    val deepDepth: Float,
    val debugTile: Boolean,
    val chartIngestWorkers: Int,
    val useTileCache: Boolean = true,
    val enableIngestion: Boolean = true,
    val regionExports: List<RegionExportConfig> = emptyList(),
    /**
     * How many zoom levels before its compiled-scale zoom (charts.zoom) a chart starts being
     * rendered. 0 keeps the upstream behaviour; applied at tile time, so no re-ingest is needed.
     */
    val chartZoomOffset: Int = 0,
)
