import File
import io.madrona.njord.layers.Admare
import io.madrona.njord.layers.LayerableOptions
import io.madrona.njord.layers.Magvar
import io.madrona.njord.layers.Sbdare
import io.madrona.njord.layers.Tesare
import io.madrona.njord.layers.set.StandardLayers
import io.madrona.njord.model.Color
import io.madrona.njord.model.Depth
import io.madrona.njord.model.LayerType
import io.madrona.njord.model.ThemeMode
import io.madrona.njord.model.colorFrom
import io.madrona.njord.resources
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SBDARE, MAGVAR, ADMARE and TESARE were in every tile but not registered, so nothing was drawn.
 * Their portrayal follows the S-101 portrayal catalogue rules; placement is checked in the browser.
 */
class SeabedMagvarTest {

    @BeforeTest
    fun setup() {
        resources = File("./src/nativeMain/resources").getAbsolutePath().toString()
    }

    private val options = LayerableOptions(Depth.METERS, ThemeMode.Day)

    @Test
    fun `seabed text uses the S-101 abbreviations joined by a space`() {
        assertEquals("R", Sbdare.seabedText(listOf("9")))
        assertEquals("S Sh", Sbdare.seabedText(listOf("4", "17")))
        assertEquals("M S G", Sbdare.seabedText(listOf("1", "4", "6")))
        // lava and boulders read as rock
        assertEquals("R R", Sbdare.seabedText(listOf("11", "18")))
        // SHOM layered surface: sand over rock
        assertEquals("S/R", Sbdare.seabedText(listOf("4/9")))
    }

    @Test
    fun `seabed text is null when no code is known`() {
        assertNull(Sbdare.seabedText(emptyList()))
        assertNull(Sbdare.seabedText(listOf("99")))
        assertNull(Sbdare.seabedText(listOf("x")))
    }

    @Test
    fun `magnetic variation uses the S-101 symbols and reads VALMAG in the style`() {
        val layers = Magvar().layers(options).toList()
        val json = layers.toString()
        assertTrue(json.contains("MAGVAR01") && json.contains("MAGVAR51"))
        val lineLabel = layers.first { it.id == "MAGVAR_line_label" }.layout?.textField.toString()
        assertTrue(lineLabel.contains("\"varn \"") && lineLabel.contains("\"VALMAG\""))
    }

    @Test
    fun `limits are dashed CHGRF lines without fill`() {
        listOf(Admare(), Tesare()).forEach { layerable ->
            val line = layerable.layers(options).first()
            assertEquals(LayerType.LINE, line.type)
            assertTrue(line.paint?.lineColor.toString().contains(colorFrom(Color.CHGRF, ThemeMode.Day)))
            assertTrue(layerable.layers(options).none { it.type == LayerType.FILL })
        }
    }

    @Test
    fun `all four are registered in the standard layers`() {
        val keys = StandardLayers().layers.map { it.key }.toSet()
        assertTrue(keys.containsAll(listOf("SBDARE", "MAGVAR", "ADMARE", "TESARE")))
    }
}
