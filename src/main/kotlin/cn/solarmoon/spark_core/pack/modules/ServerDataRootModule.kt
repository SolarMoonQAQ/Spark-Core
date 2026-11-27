package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.SparkPackLoaderApplier
import cn.solarmoon.spark_core.pack.diagnostic.PackContentException
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType

internal abstract class ServerDataRootModule(
    final override val id: String,
    private val extension: String,
) : SparkPackModule {

    final override val mode: ReadMode = ReadMode.SERVER_LOCAL_ONLY
    private var count = 0

    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (!isClientSide) count = 0
    }

    final override fun read(
        namespace: String,
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean,
        fromServer: Boolean,
    ) {
        if (isClientSide) return

        val location = resourceLocation(namespace, pathSegments, fileName)
        SparkPackLoaderApplier.DATA_PACK.put(PackType.SERVER_DATA, location, content)
        count++
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (!isClientSide) {
            SparkCore.LOGGER.info("从外部包注入了{}个数据包资源到 {} 模块", count, id)
        }
    }

    internal fun resourceLocation(
        namespace: String,
        pathSegments: List<String>,
        fileName: String,
    ): ResourceLocation {
        if (!fileName.endsWith(extension)) {
            throw PackContentException(
                Component.literal("Expected a $extension file in the '$id' module")
            )
        }

        val relativeSegments = pathSegments + fileName
        if (relativeSegments.any {
                it.isEmpty() || it == "." || it == ".." || '/' in it || '\\' in it
            }
        ) {
            throw PackContentException(
                Component.literal("Invalid relative path in the '$id' module")
            )
        }

        val path = (listOf(id) + pathSegments + fileName).joinToString("/")
        return ResourceLocation.fromNamespaceAndPath(namespace, path)
    }
}

internal class WorldgenModule : ServerDataRootModule("worldgen", ".json")
internal class StructureModule : ServerDataRootModule("structure", ".nbt")
internal class TagsModule : ServerDataRootModule("tags", ".json")
internal class LootTableModule : ServerDataRootModule("loot_table", ".json")
