import io.madrona.njord.geojson.Polygon
import io.madrona.njord.geojson.Position

/**
 * Returns a valid version of an M_COVR coverage polygon.
 *
 * GDAL sometimes assembles an M_COVR boundary into open fragments: an unclosed "exterior ring"
 * plus unclosed "interior rings" that are in fact other pieces of the same boundary, with gaps
 * between them (seen on PL5MRZEZ.000). PostGIS stores such a polygon, but every GEOS operation on
 * it fails, and since tiles select charts with `st_intersects(tile, covr)`, one bad coverage broke
 * every tile containing it, at every zoom.
 *
 * Valid polygons are returned unchanged. Otherwise the rings are chained end to start (nearest
 * endpoint first, reversing a fragment when needed) into a single closed ring. If that is still not
 * valid, the polygon's envelope is used: a coarser coverage beats a chart that breaks tiles.
 */
fun repairCoverage(polygon: Polygon): Polygon {
    if (OgrGeometry.fromGeoJson4326(polygon)?.isValid == true) return polygon

    val stitched = stitchRings(polygon.coordinates)
    if (stitched != null && OgrGeometry.fromGeoJson4326(stitched)?.isValid == true) return stitched

    return envelopeOf(polygon)
}

private fun stitchRings(rings: List<List<Position>>): Polygon? {
    val fragments = rings.map { ring ->
        if (ring.size > 1 && ring.first() == ring.last()) ring.dropLast(1) else ring
    }.filter { it.isNotEmpty() }.toMutableList()
    if (fragments.isEmpty()) return null

    val chain = fragments.removeAt(0).toMutableList()
    while (fragments.isNotEmpty()) {
        val end = chain.last()
        var bestIndex = 0
        var bestReversed = false
        var bestDistance = Double.MAX_VALUE
        fragments.forEachIndexed { i, fragment ->
            val toStart = end.distanceSquared(fragment.first())
            val toEnd = end.distanceSquared(fragment.last())
            if (toStart < bestDistance) {
                bestIndex = i; bestReversed = false; bestDistance = toStart
            }
            if (toEnd < bestDistance) {
                bestIndex = i; bestReversed = true; bestDistance = toEnd
            }
        }
        val next = fragments.removeAt(bestIndex)
        chain.addAll(if (bestReversed) next.reversed() else next)
    }
    if (chain.size < 3) return null
    return Polygon(listOf(chain + chain.first()))
}

private fun Position.distanceSquared(other: Position): Double {
    val dx = x - other.x
    val dy = y - other.y
    return dx * dx + dy * dy
}

private fun envelopeOf(polygon: Polygon): Polygon {
    val positions = polygon.coordinates.flatten()
    val west = positions.minOf { it.x }
    val east = positions.maxOf { it.x }
    val south = positions.minOf { it.y }
    val north = positions.maxOf { it.y }
    return Polygon(
        listOf(
            listOf(
                Position(west, south),
                Position(east, south),
                Position(east, north),
                Position(west, north),
                Position(west, south),
            )
        )
    )
}
