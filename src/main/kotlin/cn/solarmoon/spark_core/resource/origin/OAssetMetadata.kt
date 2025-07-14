package cn.solarmoon.spark_core.resource.origin

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

/**
 * 资源元数据
 *
 * 只负责描述资源自身的、非关系型的元数据。
 *
 * @param id 资源的唯一ID (ResourceLocation.toString())
 * @param provides 提供的接口或特性
 * @param tags 标签
 * @param properties 额外属性
 */
data class OAssetMetadata(
    val id: String = "",
    val provides: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val properties: Map<String, Any> = emptyMap()
) {


    companion object {

        const val META_FILE_SUFFIX = ".meta.json"

        fun get(res: ResourceLocation): OAssetMetadata? = ORIGINS[res]

        /**
         * 静态存储 - 仿照 OModel.ORIGINS 模式
         */
        @JvmStatic
        val ORIGINS = linkedMapOf<ResourceLocation, OAssetMetadata>()

        /**
         * 空的元数据
         */
        @JvmStatic
        val EMPTY = OAssetMetadata()

        /**
         * Mojang Codec for OAssetMetadata
         */
        @JvmStatic
        val CODEC: Codec<OAssetMetadata> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter(OAssetMetadata::id),
                Codec.STRING.listOf().optionalFieldOf("provides", emptyList()).forGetter(OAssetMetadata::provides),
                Codec.STRING.listOf().optionalFieldOf("tags", emptyList()).forGetter(OAssetMetadata::tags),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("properties", emptyMap()).forGetter {
                    // Convert Map<String, Any> to Map<String, String> for serialization
                    it.properties.mapValues { entry -> entry.value.toString() }
                }
            ).apply(instance) { id, provides, tags, properties ->
                OAssetMetadata(
                    id = id,
                    provides = provides,
                    tags = tags,
                    properties = properties
                )
            }
        }

        /**
         * Stream codec for network serialization
         */
        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, OAssetMetadata> {
            override fun decode(buffer: FriendlyByteBuf): OAssetMetadata {
                val id = buffer.readUtf()
                val provides = readStringList(buffer)
                val tags = readStringList(buffer)
                val properties = readStringAnyMap(buffer)

                return OAssetMetadata(
                    id = id,
                    provides = provides,
                    tags = tags,
                    properties = properties
                )
            }

            override fun encode(buffer: FriendlyByteBuf, value: OAssetMetadata) {
                buffer.writeUtf(value.id)
                writeStringList(buffer, value.provides)
                writeStringList(buffer, value.tags)
                writeStringAnyMap(buffer, value.properties)
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

            private fun readStringAnyMap(buffer: FriendlyByteBuf): Map<String, Any> {
                val size = buffer.readVarInt()
                val map = mutableMapOf<String, Any>()
                for (i in 0 until size) {
                    val key = buffer.readUtf()
                    when (buffer.readByte()) {
                        0.toByte() -> map[key] = buffer.readUtf() // String
                        1.toByte() -> map[key] = buffer.readBoolean() // Boolean
                        2.toByte() -> map[key] = buffer.readInt() // Int
                        else -> map[key] = buffer.readUtf() // Fallback to String
                    }
                }
                return map
            }

            private fun writeStringAnyMap(buffer: FriendlyByteBuf, map: Map<String, Any>) {
                buffer.writeVarInt(map.size)
                for ((key, value) in map) {
                    buffer.writeUtf(key)
                    when (value) {
                        is String -> {
                            buffer.writeByte(0)
                            buffer.writeUtf(value)
                        }
                        is Boolean -> {
                            buffer.writeByte(1)
                            buffer.writeBoolean(value)
                        }
                        is Int -> {
                            buffer.writeByte(2)
                            buffer.writeInt(value)
                        }
                        else -> {
                            buffer.writeByte(0)
                            buffer.writeUtf(value.toString())
                        }
                    }
                }
            }
        }

        /**
         * Origins map stream codec for network synchronization
         */
        @JvmStatic
        val ORIGIN_MAP_STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, LinkedHashMap<ResourceLocation, OAssetMetadata>> {
            override fun decode(buffer: FriendlyByteBuf): LinkedHashMap<ResourceLocation, OAssetMetadata> {
                val size = buffer.readVarInt()
                val map = LinkedHashMap<ResourceLocation, OAssetMetadata>()

                for (i in 0 until size) {
                    val location = ResourceLocation.STREAM_CODEC.decode(buffer)
                    val metadata = STREAM_CODEC.decode(buffer)
                    map[location] = metadata
                }

                return map
            }

            override fun encode(buffer: FriendlyByteBuf, value: LinkedHashMap<ResourceLocation, OAssetMetadata>) {
                buffer.writeVarInt(value.size)

                for ((location, metadata) in value) {
                    ResourceLocation.STREAM_CODEC.encode(buffer, location)
                    STREAM_CODEC.encode(buffer, metadata)
                }
            }
        }
    }
}