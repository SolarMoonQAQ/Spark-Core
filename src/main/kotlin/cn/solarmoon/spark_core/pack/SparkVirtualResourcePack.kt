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
import kotlin.collections.component1
import kotlin.collections.component2

class SparkVirtualResourcePack(val type: PackType, val id: String) : PackResources {

    val SPARK_RESOURCE_PACK_LOCATION = PackLocationInfo(
        id,
        Component.literal("Spark External Content"),
        PackSource.BUILT_IN,
        Optional.empty()
    )

    // PackType -> ResourceLocation -> bytes
    private val resources = EnumMap<PackType, MutableMap<ResourceLocation, ByteArray>>(PackType::class.java)

    init {
        PackType.entries.forEach {
            resources[it] = mutableMapOf()
        }
    }

    fun size(type: PackType) = resources[type]?.size ?: 0

    fun put(
        type: PackType,
        id: ResourceLocation,
        bytes: ByteArray
    ) {
        this.resources[type]!![id] = bytes
    }

    override fun getResource(
        type: PackType,
        location: ResourceLocation
    ): IoSupplier<InputStream>? {
        val bytes = resources[type]?.get(location) ?: return null
        return IoSupplier { ByteArrayInputStream(bytes) }
    }

    override fun listResources(
        type: PackType,
        namespace: String,
        path: String,
        output: PackResources.ResourceOutput
    ) {
        resources[type]!!
            .filter { (id, _) ->
                id.namespace == namespace && id.path.startsWith(path)
            }
            .forEach { (id, data) ->
                output.accept(id) { ByteArrayInputStream(data) }
            }
    }


    override fun getNamespaces(type: PackType): Set<String> =
        resources[type]!!.keys.mapTo(HashSet()) { it.namespace }

    override fun <T> getMetadataSection(serializer: MetadataSectionSerializer<T>): T? {
        if (serializer == PackMetadataSection.TYPE) {
            @Suppress("UNCHECKED_CAST")
            return PackMetadataSection(
                Component.literal("Spark External Content Pack"),
                SharedConstants.getCurrentVersion().getPackVersion(type)
            ) as T
        }
        return null
    }

    override fun getRootResource(vararg paths: String): IoSupplier<InputStream>? = null
    override fun location(): PackLocationInfo = SPARK_RESOURCE_PACK_LOCATION
    override fun close() {}

}

