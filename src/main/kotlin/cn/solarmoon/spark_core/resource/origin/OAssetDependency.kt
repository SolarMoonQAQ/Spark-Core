package cn.solarmoon.spark_core.resource.origin

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import java.util.Optional

/**
 * 资源依赖
 */
data class OAssetDependency(
    /** 依赖的资源ID */
    val id: String,

    /** 依赖类型 */
    val type: ODependencyType = ODependencyType.HARD,

    /** 版本约束（可选） */
    val version: String? = null,

    /** 描述信息 */
    val description: String? = null
) {
    /** 解析为ResourceLocation */
    fun toResourceLocation(): ResourceLocation {
        return ResourceLocation.parse(id)
    }

    companion object {
        /**
         * Mojang Codec for OAssetDependency
         */
        @JvmStatic
        val CODEC: Codec<OAssetDependency> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter(OAssetDependency::id),
                ODependencyType.CODEC.optionalFieldOf("type", ODependencyType.HARD).forGetter(OAssetDependency::type),
                Codec.STRING.optionalFieldOf("version").forGetter { Optional.ofNullable(it.version) },
                Codec.STRING.optionalFieldOf("description").forGetter { Optional.ofNullable(it.description) }
            ).apply(instance) { id, type, version, description ->
                OAssetDependency(
                    id = id,
                    type = type,
                    version = version.orElse(null),
                    description = description.orElse(null)
                )
            }
        }

        /**
         * Stream codec for network serialization
         */
        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, OAssetDependency> {
            override fun decode(buffer: FriendlyByteBuf): OAssetDependency {
                val id = buffer.readUtf()
                val type = ODependencyType.STREAM_CODEC.decode(buffer)
                val version = if (buffer.readBoolean()) buffer.readUtf() else null
                val description = if (buffer.readBoolean()) buffer.readUtf() else null

                return OAssetDependency(
                    id = id,
                    type = type,
                    version = version,
                    description = description
                )
            }

            override fun encode(buffer: FriendlyByteBuf, value: OAssetDependency) {
                buffer.writeUtf(value.id)
                ODependencyType.STREAM_CODEC.encode(buffer, value.type)

                buffer.writeBoolean(value.version != null)
                value.version?.let { buffer.writeUtf(it) }

                buffer.writeBoolean(value.description != null)
                value.description?.let { buffer.writeUtf(it) }
            }
        }

        /**
         * List codec for dependency collections
         */
        @JvmStatic
        val LIST_CODEC: Codec<List<OAssetDependency>> = CODEC.listOf()

        /**
         * Stream codec for dependency lists
         */
        @JvmStatic
        val LIST_STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, List<OAssetDependency>> {
            override fun decode(buffer: FriendlyByteBuf): List<OAssetDependency> {
                val size = buffer.readVarInt()
                val list = ArrayList<OAssetDependency>(size)
                for (i in 0 until size) {
                    list.add(STREAM_CODEC.decode(buffer))
                }
                return list
            }

            override fun encode(buffer: FriendlyByteBuf, value: List<OAssetDependency>) {
                buffer.writeVarInt(value.size)
                for (item in value) {
                    STREAM_CODEC.encode(buffer, item)
                }
            }
        }
    }
}