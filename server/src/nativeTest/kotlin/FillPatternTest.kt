import File
import io.madrona.njord.layers.LayerFactory
import io.madrona.njord.layers.LayerableOptions
import io.madrona.njord.model.Depth
import io.madrona.njord.model.ThemeMode
import io.madrona.njord.resources
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A fill pattern read from the feature's `AP` must be filtered on `AP` being present, or MapLibre
 * asks for an image named `null` on every feature without one (LNDRGN, DRGARE, OBSTRN areas).
 */
class FillPatternTest {

    @BeforeTest
    fun setup() {
        resources = File("./src/nativeMain/resources").getAbsolutePath().toString()
    }

    @Test
    fun `every fill pattern read from AP is filtered on AP`() {
        val layers = LayerFactory().layers(LayerableOptions(Depth.METERS, ThemeMode.Day))
        val fromAp = layers.filter { it.paint?.fillPattern.toString() == """["get","AP"]""" }
        assertTrue(fromAp.isNotEmpty())
        val unfiltered = fromAp.filterNot { it.filter.toString().contains("""["has","AP"]""") }.map { it.id }
        assertTrue(unfiltered.isEmpty(), "unfiltered: $unfiltered")
    }
}
