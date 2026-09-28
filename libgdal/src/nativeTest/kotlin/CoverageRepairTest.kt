import io.madrona.njord.geojson.Polygon
import io.madrona.njord.geojson.Position
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CoverageRepairTest {

    @BeforeTest
    fun beforeEach() {
        Gdal.initialize()
    }

    private fun p(x: Double, y: Double) = Position(x, y)

    private fun Polygon.isValidOgr(): Boolean = OgrGeometry.fromGeoJson4326(this)?.isValid == true

    @Test
    fun validCoverageIsReturnedUnchanged() {
        val square = Polygon(listOf(listOf(p(0.0, 0.0), p(1.0, 0.0), p(1.0, 1.0), p(0.0, 1.0), p(0.0, 0.0))))
        assertSame(square, repairCoverage(square))
    }

    /**
     * Shape of PL5MRZEZ.000 (Kołobrzeg) as GDAL assembled its M_COVR: the boundary came out as one
     * open "exterior ring" plus open fragments stored as interior rings, with gaps between them.
     * Chained end-to-start (0 → 5 → 1 → 2 → 3 → 4) they trace the whole coverage.
     */
    @Test
    fun fragmentedBoundaryIsStitchedIntoOneRing() {
        val broken = Polygon(
            listOf(
                listOf(p(15.2891666, 54.1475548), p(15.2891666, 54.1450), p(15.2873131, 54.1416667)), // 0
                listOf(p(15.2862057, 54.1416667), p(15.2855, 54.1416667), p(15.2849762, 54.1416667)), // 1
                listOf(p(15.2825001, 54.1416975), p(15.2825001, 54.1450), p(15.2825001, 54.1481297)), // 2
                listOf(p(15.2825001, 54.1491667), p(15.2860, 54.1491667), p(15.2891666, 54.1491028)), // 3
                listOf(p(15.2891666, 54.1488049), p(15.2891666, 54.1486), p(15.2891666, 54.14839)),   // 4
                listOf(p(15.2864051, 54.1416667), p(15.2863, 54.1416667), p(15.2862057, 54.1416667)), // 5
            )
        )
        assertTrue(!broken.isValidOgr())

        val repaired = repairCoverage(broken)

        assertEquals(1, repaired.coordinates.size, "a single ring")
        val ring = repaired.coordinates.first()
        assertEquals(ring.first(), ring.last(), "ring is closed")
        assertEquals(18 + 1, ring.size, "every input position is kept, plus the closing one")
        assertTrue(repaired.isValidOgr())
        val envelope = assertNotNull(OgrGeometry.fromGeoJson4326(repaired)).envelope()
        assertEquals(15.2825001, envelope.west)
        assertEquals(15.2891666, envelope.east)
        assertEquals(54.1416667, envelope.south)
        assertEquals(54.1491667, envelope.north)
    }

    @Test
    fun reversedFragmentIsFollowedInItsOwnDirection() {
        // Unit square boundary cut in two, the second piece stored backwards.
        val broken = Polygon(
            listOf(
                listOf(p(0.0, 0.0), p(1.0, 0.0), p(1.0, 0.5)),
                listOf(p(0.0, 0.5), p(0.0, 1.0), p(1.0, 1.0)),
            )
        )
        val repaired = repairCoverage(broken)
        assertEquals(1, repaired.coordinates.size)
        assertTrue(repaired.isValidOgr())
    }

    @Test
    fun unrepairableCoverageFallsBackToItsEnvelope() {
        // A self-crossing bow tie cannot be fixed by stitching.
        val bowTie = Polygon(listOf(listOf(p(0.0, 0.0), p(1.0, 1.0), p(1.0, 0.0), p(0.0, 1.0), p(0.0, 0.0))))
        val repaired = repairCoverage(bowTie)
        assertTrue(repaired.isValidOgr())
        assertEquals(
            listOf(p(0.0, 0.0), p(1.0, 0.0), p(1.0, 1.0), p(0.0, 1.0), p(0.0, 0.0)),
            repaired.coordinates.single()
        )
    }
}
