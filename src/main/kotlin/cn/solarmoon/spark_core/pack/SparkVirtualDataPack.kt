package cn.solarmoon.spark_core.pack

import net.minecraft.SharedConstants
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.MetadataSectionSerializer
import net.minecraft.server.packs.metadata.pack.PackMetadataSection
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.IoSupplier
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.EnumMap
import java.util.Optional

class SparkVirtualDataPack : PackResources {

    val SPARK_DATA_PACK_LOCATION = PackLocationInfo(
        "spark_external_data",
        Component.literal("Spark External Content"),
        PackSource.BUILT_IN,
        Optional.empty()
    )

    private val data =
        EnumMap<PackType, MutableMap<ResourceLocation, ByteArray>>(PackType::class.java)

    init {
        PackType.entries.forEach {
            data[it] = HashMap()
        }
    }

    fun put(
        type: PackType,
        id: ResourceLocation,
        data: ByteArray
    ) {
        this.data[type]!![id] = data
    }

    override fun getResource(
        type: PackType,
        location: ResourceLocation
    ): IoSupplier<InputStream>? {
        val data = data[type]?.get(location) ?: return null
        return IoSupplier { ByteArrayInputStream(data) }
    }

    override fun listResources(
        type: PackType,
        namespace: String,
        path: String,
        output: PackResources.ResourceOutput
    ) {
        data[type]!!
            .filter { (id, _) ->
                id.namespace == namespace && id.path.startsWith(path)
            }
            .forEach { (id, data) ->
                output.accept(id) { ByteArrayInputStream(data) }
            }
    }

    override fun getNamespaces(type: PackType): Set<String> =
        data[type]!!.keys.mapTo(HashSet()) { it.namespace }

    override fun <T> getMetadataSection(serializer: MetadataSectionSerializer<T>): T? {
        if (serializer == PackMetadataSection.TYPE) {
            @Suppress("UNCHECKED_CAST")
            return PackMetadataSection(
                Component.literal("Spark External Content Pack"),
                SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES)
            ) as T
        }
        return null
    }

    override fun getRootResource(vararg paths: String): IoSupplier<InputStream>? = null
    override fun location(): PackLocationInfo = SPARK_DATA_PACK_LOCATION
    override fun close() {}
}
