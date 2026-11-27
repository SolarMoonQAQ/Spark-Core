package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.pack.SparkPackLoaderApplier
import cn.solarmoon.spark_core.pack.diagnostic.PackContentException
import cn.solarmoon.spark_core.pack.graph.SparkPackMetaInfo
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ServerDataRootModuleTest {

    private val emptyPack = SparkPackage(
        SparkPackMetaInfo(
            ResourceLocation.fromNamespaceAndPath("test", "empty"),
            "1.0",
            Component.empty(),
            Component.empty(),
            Component.empty(),
            emptyList(),
        ),
        mutableMapOf(),
    )

    @BeforeEach
    fun setUp() {
        SparkPackLoaderApplier.DATA_PACK.clear(PackType.SERVER_DATA)
        SparkPackLoaderApplier.DATA_PACK.clear(PackType.CLIENT_RESOURCES)
    }

    @AfterEach
    fun tearDown() {
        SparkPackLoaderApplier.DATA_PACK.clear(PackType.SERVER_DATA)
        SparkPackLoaderApplier.DATA_PACK.clear(PackType.CLIENT_RESOURCES)
    }

    @Test
    fun `data roots preserve their module and nested paths`() {
        val locations = listOf(
            WorldgenModule().resourceLocation("mm", listOf("structure", "ruins"), "outpost.json") to
                ResourceLocation.parse("mm:worldgen/structure/ruins/outpost.json"),
            StructureModule().resourceLocation("mm", listOf("vehicles", "tanks"), "m1.nbt") to
                ResourceLocation.parse("mm:structure/vehicles/tanks/m1.nbt"),
            TagsModule().resourceLocation("mm", listOf("worldgen", "structure"), "vehicles.json") to
                ResourceLocation.parse("mm:tags/worldgen/structure/vehicles.json"),
            LootTableModule().resourceLocation("mm", listOf("chests"), "vehicle.json") to
                ResourceLocation.parse("mm:loot_table/chests/vehicle.json"),
        )

        locations.forEach { (actual, expected) -> assertEquals(expected, actual) }
    }

    @Test
    fun `all server data roots are server local and are not Spark-synced`() {
        val modules = listOf(WorldgenModule(), StructureModule(), TagsModule(), LootTableModule())

        for (module in modules) {
            assertEquals(ReadMode.SERVER_LOCAL_ONLY, module.mode)
            assertTrue(module.mode.shouldRead(false))
            assertFalse(module.mode.shouldRead(true))
            assertFalse(module.mode.shouldSend())
        }
    }

    @Test
    fun `module writes original bytes to the server data pack`() {
        val content = byteArrayOf(0x00, 0x01, 0x7f, 0x80.toByte(), 0xff.toByte())
        val location = ResourceLocation.parse("mm:structure/vehicles/m1.nbt")

        StructureModule().read(
            "mm",
            listOf("vehicles"),
            "m1.nbt",
            content,
            emptyPack,
            isClientSide = false,
            fromServer = true,
        )

        val resource = SparkPackLoaderApplier.DATA_PACK.getResource(PackType.SERVER_DATA, location)
        assertTrue(resource != null)
        assertArrayEquals(content, resource!!.get().use { it.readBytes() })
    }

    @Test
    fun `worldgen JSON bytes are not parsed or rewritten`() {
        val content = """{
  "type": "machine_max:spawn_vehicle",
  "processors": []
}
""".toByteArray(Charsets.UTF_8)
        val location = ResourceLocation.parse("mm:worldgen/processor_list/vehicle.json")

        WorldgenModule().read(
            "mm",
            listOf("processor_list"),
            "vehicle.json",
            content,
            emptyPack,
            isClientSide = false,
            fromServer = true,
        )

        val resource = SparkPackLoaderApplier.DATA_PACK.getResource(PackType.SERVER_DATA, location)
        assertNotNull(resource)
        assertArrayEquals(content, resource!!.get().use { it.readBytes() })
    }

    @Test
    fun `clearing server data leaves other pack types untouched`() {
        val serverLocation = ResourceLocation.parse("mm:worldgen/structure_set/test.json")
        val clientLocation = ResourceLocation.parse("mm:textures/test.png")
        val bytes = byteArrayOf(1, 2, 3)
        SparkPackLoaderApplier.DATA_PACK.put(PackType.SERVER_DATA, serverLocation, bytes)
        SparkPackLoaderApplier.DATA_PACK.put(PackType.CLIENT_RESOURCES, clientLocation, bytes)

        SparkPackLoaderApplier.DATA_PACK.clear(PackType.SERVER_DATA)

        assertNull(SparkPackLoaderApplier.DATA_PACK.getResource(PackType.SERVER_DATA, serverLocation))
        assertNotNull(SparkPackLoaderApplier.DATA_PACK.getResource(PackType.CLIENT_RESOURCES, clientLocation))
    }

    @Test
    fun `invalid extension and traversal path are reported as pack content errors`() {
        val wrongExtension = assertThrows(PackContentException::class.java) {
            WorldgenModule().resourceLocation("mm", emptyList(), "vehicle.nbt")
        }
        assertTrue(wrongExtension.userMessage.string.contains(".json"))

        assertThrows(PackContentException::class.java) {
            StructureModule().resourceLocation("mm", listOf(".."), "m1.nbt")
        }
    }
}
