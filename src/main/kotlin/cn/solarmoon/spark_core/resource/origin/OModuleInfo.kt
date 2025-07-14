package cn.solarmoon.spark_core.resource.origin

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import java.nio.file.Path
import java.util.*

/**
 * 统一且可序列化的模块信息数据类。
 * 此类替换了OModuleDescriptor。
 *
 * @param id 模块的唯一标识符。
 * @param version 模块的版本，建议使用语义化版本（SemVer）。
 * @param state 模块当前的生命周期状态。
 * @param description 模块的简要描述。
 * @param author 模块的作者或组织。
 * @param dependencies 该模块硬依赖的模块ID列表。
 * @param recommendations 软依赖的模块ID列表。
 * @param provides 此模块提供的虚拟功能列表。
 * @param conflicts 与此模块冲突的模块ID列表。
 * @param paid 未来使用的标志，指示模块是否为付费内容。
 * @param resourceDependencies 模块内所有资源依赖的映射，按键（ResourceLocation）存储。
 * @param rootPath 模块根目录的运行时文件系统路径。标记为transient以避免序列化。
 */
data class OModuleInfo(
    val id: String,
    val version: String,
    val state: OModuleState = OModuleState.DISCOVERED,
    val description: String? = null,
    val author: String? = null,
    val dependencies: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val provides: List<String> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val paid: Boolean = false,
    val resourceDependencies: Map<ResourceLocation, List<OResourceDependency>> = emptyMap(),
    @Transient val rootPath: Path? = null
) {
    companion object {
        const val DESCRIPTOR_FILE_NAME = "spark_module.json"

        @JvmStatic
        fun get(res: ResourceLocation): OModuleInfo? = ORIGINS[res]

        @JvmStatic
        val ORIGINS = linkedMapOf<ResourceLocation, OModuleInfo>()

        @JvmStatic
        val CODEC: Codec<OModuleInfo> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter(OModuleInfo::id),
                Codec.STRING.fieldOf("version").forGetter(OModuleInfo::version),
                OModuleState.CODEC.optionalFieldOf("state", OModuleState.DISCOVERED).forGetter(OModuleInfo::state),
                Codec.STRING.optionalFieldOf("description").forGetter { Optional.ofNullable(it.description) },
                Codec.STRING.optionalFieldOf("author").forGetter { Optional.ofNullable(it.author) },
                Codec.STRING.listOf().optionalFieldOf("depends", emptyList()).forGetter(OModuleInfo::dependencies),
                Codec.STRING.listOf().optionalFieldOf("recommends", emptyList()).forGetter(OModuleInfo::recommendations),
                Codec.STRING.listOf().optionalFieldOf("provides", emptyList()).forGetter(OModuleInfo::provides),
                Codec.STRING.listOf().optionalFieldOf("conflicts", emptyList()).forGetter(OModuleInfo::conflicts),
                Codec.BOOL.optionalFieldOf("paid", false).forGetter(OModuleInfo::paid),
                Codec.unboundedMap(ResourceLocation.CODEC, OResourceDependency.LIST_CODEC).optionalFieldOf("resourceDependencies", emptyMap()).forGetter(OModuleInfo::resourceDependencies)
            ).apply(instance) { id, version, state, desc, auth, deps, recs, prov, conf, pd, resDeps ->
                OModuleInfo(id, version, state, desc.orElse(null), auth.orElse(null), deps, recs, prov, conf, pd, resDeps, null)
            }
        }

        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, OModuleInfo> {
            override fun decode(buffer: FriendlyByteBuf): OModuleInfo {
                val id = buffer.readUtf()
                val version = buffer.readUtf()
                val state = OModuleState.STREAM_CODEC.decode(buffer)
                val description = if (buffer.readBoolean()) buffer.readUtf() else null
                val author = if (buffer.readBoolean()) buffer.readUtf() else null
                val depends = readStringList(buffer)
                val recommends = readStringList(buffer)
                val provides = readStringList(buffer)
                val conflicts = readStringList(buffer)
                val paid = buffer.readBoolean()
                val resourceDependencies = ByteBufCodecs.map(::LinkedHashMap, ResourceLocation.STREAM_CODEC, OResourceDependency.LIST_STREAM_CODEC).decode(buffer)

                return OModuleInfo(
                    id, version, state, description, author, depends, recommends, provides, conflicts, paid, resourceDependencies, null
                )
            }

            override fun encode(buffer: FriendlyByteBuf, value: OModuleInfo) {
                buffer.writeUtf(value.id)
                buffer.writeUtf(value.version)
                OModuleState.STREAM_CODEC.encode(buffer, value.state)

                buffer.writeBoolean(value.description != null)
                value.description?.let { buffer.writeUtf(it) }

                buffer.writeBoolean(value.author != null)
                value.author?.let { buffer.writeUtf(it) }

                writeStringList(buffer, value.dependencies)
                writeStringList(buffer, value.recommendations)
                writeStringList(buffer, value.provides)
                writeStringList(buffer, value.conflicts)

                buffer.writeBoolean(value.paid)
                val mapData = if (value.resourceDependencies is LinkedHashMap) {
                    value.resourceDependencies
                } else {
                    LinkedHashMap(value.resourceDependencies)
                }
                ByteBufCodecs.map(::LinkedHashMap, ResourceLocation.STREAM_CODEC, OResourceDependency.LIST_STREAM_CODEC).encode(buffer, mapData)
            }

            private fun readStringList(buffer: FriendlyByteBuf): List<String> {
                val size = buffer.readVarInt()
                val list = mutableListOf<String>()
                for (i in 0 until size) {
                    list.add(buffer.readUtf())
                }
                return list
            }

            private fun writeStringList(buffer: FriendlyByteBuf, list: List<String>) {
                buffer.writeVarInt(list.size)
                for (item in list) {
                    buffer.writeUtf(item)
                }
            }
        }
    }
} 