import File
import io.madrona.njord.layers.Depcnt
import io.madrona.njord.layers.LayerableOptions
import io.madrona.njord.layers.set.StandardLayers
import io.madrona.njord.model.Depth
import io.madrona.njord.model.Layer
import io.madrona.njord.model.LayerType
import io.madrona.njord.model.Placement
import io.madrona.njord.model.ThemeMode
import io.madrona.njord.resources
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Depth contours were in every tile but had no style layer, so MapLibre drew nothing for them.
 * The label value is computed by the style from VALDCO (metres); the strings it produces are
 * checked against a real chart in the browser.
 */
class DepcntTest {

    @BeforeTest
    fun setup() {
        resources = File("./src/nativeMain/resources").getAbsolutePath().toString()
    }

    private fun layers(depth: Depth): List<Layer> =
        Depcnt().layers(LayerableOptions(depth, ThemeMode.Day)).toList()

    private fun label(depth: Depth): String =
        layers(depth).first { it.id == "DEPCNT_label" }.layout?.textField.toString()

    @Test
    fun `draws a line and a label placed along it`() {
        val (line, label) = layers(Depth.METERS)
        assertEquals("DEPCNT_line", line.id)
        assertEquals(LayerType.LINE, line.type)
        assertEquals("DEPCNT_label", label.id)
        assertEquals(Placement.LINE, label.layout?.symbolPlacement)
        listOf(line, label).forEach { assertEquals("DEPCNT", it.sourceLayer) }
        assertTrue(label.filter.toString().contains("[\">=\",\"VALDCO\",0]"))
    }

    @Test
    fun `label reads VALDCO and converts it per depth unit`() {
        assertTrue(label(Depth.METERS).startsWith("[\"case\",[\"<\",[\"get\",\"VALDCO\"],31]"))
        assertTrue(label(Depth.FEET).contains("3.28084"))
        assertTrue(label(Depth.FEET).contains("\"round\""))
        assertTrue(label(Depth.FATHOMS).contains("0.546807"))
    }

    @Test
    fun `is part of the standard layers right above depth areas`() {
        val keys = StandardLayers().layers.map { it.key }.toList()
        assertEquals(keys.indexOf("DEPARE") + 1, keys.indexOf("DEPCNT"))
    }
}
