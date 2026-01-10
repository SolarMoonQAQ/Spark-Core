package cn.solarmoon.spark_core.pack

import net.minecraft.SharedConstants
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.MetadataSectionSerializer
import net.minecraft.server.packs.metadata.pack.PackMetadataSection
import net.minecraft.server.packs.resources.IoSupplier
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.EnumMap

class SparkVirtualResourcePack(
    private val location: PackLocationInfo
) : PackResources {

    // PackType -> namespace -> path -> bytes
    private val resources =
        EnumMap<PackType, MutableMap<String, MutableMap<String, ByteArray>>>(PackType::class.java)

    init {
        PackType.entries.forEach {
            resources[it] = mutableMapOf()
        }
    }

    fun clear() {
        resources.values.forEach { nsMap ->
            nsMap.values.forEach { it.clear() }
            nsMap.clear()
        }
    }

    fun put(
        type: PackType,
        id: ResourceLocation,
        bytes: ByteArray
    ) {
        val nsMap = resources[type]!!
        val pathMap = nsMap.computeIfAbsent(id.namespace) { mutableMapOf() }
        pathMap[id.path] = bytes
    }

    override fun getResource(
        type: PackType,
        location: ResourceLocation
    ): IoSupplier<InputStream>? {
        val bytes = resources[type]
            ?.get(location.namespace)
            ?.get(location.path)
            ?: return null

        return IoSupplier { ByteArrayInputStream(bytes) }
    }

    override fun listResources(
        type: PackType,
        namespace: String,
        path: String,
        output: PackResources.ResourceOutput
    ) {
        val nsMap = resources[type] ?: return
        val pathMap = nsMap[namespace] ?: return

        for ((resPath, bytes) in pathMap) {
            if (resPath.startsWith(path)) {
                output.accept(
                    ResourceLocation.fromNamespaceAndPath(namespace, resPath),
                    IoSupplier { ByteArrayInputStream(bytes) }
                )
            }
        }
    }

    override fun getNamespaces(type: PackType): Set<String> {
        return resources[type]?.keys ?: emptySet()
    }

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
    override fun location(): PackLocationInfo = location
    override fun close() {}

}

