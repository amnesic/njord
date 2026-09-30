import File
import io.madrona.njord.layers.LayerFactory
import io.madrona.njord.layers.LayerableOptions
import io.madrona.njord.layers.S101ViewingGroups
import io.madrona.njord.model.Depth
import io.madrona.njord.model.Layer
import io.madrona.njord.model.ThemeMode
import io.madrona.njord.resources
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Each style layer carries its S-101 viewing group so clients can offer the S-101 display modes
 * or their own selection. The table comes from `s101/extract_viewing_groups.py`.
 */
class S101ViewingGroupsTest {

    @BeforeTest
    fun setup() {
        resources = File("./src/nativeMain/resources").getAbsolutePath().toString()
    }

    private val layers: List<Layer> by lazy {
        LayerFactory(s101 = S101ViewingGroups()).layers(LayerableOptions(Depth.METERS, ThemeMode.Day))
    }

    private fun meta(id: String, key: String): String? =
        layers.first { it.id == id }.metadata?.jsonObject?.get(key)?.jsonPrimitive?.content

    @Test
    fun `layers carry the S-101 feature and viewing group and layer and display mode`() {
        assertEquals("SeabedArea", meta("SBDARE_label", "s101:feature"))
        assertEquals("34010", meta("SBDARE_label", "s101:viewingGroup"))
        assertEquals("16", meta("SBDARE_label", "s101:viewingGroupLayer"))
        assertEquals("OtherInformation", meta("SBDARE_label", "s101:displayMode"))
        // the most specific layer: Lights, not Buoys, beacons, aids to navigation
        val light = layers.first { it.sourceLayer == "LIGHTS" }.id
        assertEquals("3b", meta(light, "s101:viewingGroupLayer"))
        assertEquals("StandardDisplay", meta(light, "s101:displayMode"))
    }

    @Test
    fun `text-only layers also carry their text viewing group`() {
        assertEquals("25", meta("SBDARE_label", "s101:textViewingGroup"))
        assertEquals("90031", meta("DEPCNT_label", "s101:textViewingGroup"))
        assertEquals(null, meta("DEPCNT_line", "s101:textViewingGroup"))
    }

    @Test
    fun `the world base map is flagged and not given an S-101 feature`() {
        val background = layers.first { it.sourceLayer == null }
        assertEquals("true", background.metadata?.jsonObject?.get("njord:basemap")?.jsonPrimitive?.content)
    }

    @Test
    fun `nearly every chart layer is mapped`() {
        val unmapped = layers.filter { it.metadata == null }.mapNotNull { it.sourceLayer }.toSet()
        // no S-101 feature type or rule for these (removed, remodelled, or njord's own)
        assertTrue(unmapped.all { it in setOf("ACHPNT", "CTRPNT", "ICNARE", "SWPARE", "TCTLPT") }, "$unmapped")
    }

    @Test
    fun `style root lists the three display modes and the viewing group layers`() {
        val root = assertNotNull(S101ViewingGroups().styleMetadata).jsonObject
        val modes = root["s101:displayModes"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        assertEquals(listOf("DisplayBase", "StandardDisplay", "OtherInformation"), modes)
        assertTrue(root["s101:viewingGroupLayers"]!!.jsonArray.size >= 30)
    }
}
