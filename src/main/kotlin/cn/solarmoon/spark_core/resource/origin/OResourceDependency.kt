package cn.solarmoon.spark_core.resource.origin

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import java.util.Optional
import io.netty.buffer.ByteBuf

/**
 * 统一的资源依赖类
 * 遵循标准序列化模式：Codec用于JSON，StreamCodec用于网络传输
 */
data class OResourceDependency(
    /** 依赖的资源ID */
    val id: ResourceLocation,

    /** 依赖类型 */
    val type: ODependencyType,

    /** 依赖资源的路径 */
    val path: String,

    /** 额外属性 */
    val extraProps: Map<String, Any> = emptyMap(),

    /** 版本约束（可选） */
    val version: String? = null,

    /** 描述信息 */
    val description: String? = null
) {
    

    
    companion object {

        
        /**
         * Mojang Codec for JSON serialization
         */
        @JvmStatic
        val CODEC: Codec<OResourceDependency> = RecordCodecBuilder.create { instance ->
            instance.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(OResourceDependency::id),
                ODependencyType.CODEC.fieldOf("type").forGetter(OResourceDependency::type),
                Codec.STRING.fieldOf("path").forGetter(OResourceDependency::path),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("extraProps", emptyMap()).forGetter { 
                    // Convert Map<String, Any> to Map<String, String> for serialization
                    it.extraProps.mapValues { entry -> entry.value.toString() }
                },
                Codec.STRING.optionalFieldOf("version").forGetter { Optional.ofNullable(it.version) },
                Codec.STRING.optionalFieldOf("description").forGetter { Optional.ofNullable(it.description) }
            ).apply(instance) { id, type, path, extraProps, version, description ->
                OResourceDependency(
                    id = id,
                    type = type,
                    path = path,
                    extraProps = extraProps,
                    version = version.orElse(null),
                    description = description.orElse(null)
                )
            }
        }
        
        /**
         * Stream codec for network serialization
         */
        private const val TYPE_STRING: Byte = 0
        private const val TYPE_BOOLEAN: Byte = 1
        private const val TYPE_INT: Byte = 2

        /**
         * 支持 String/Boolean/Int 的 Map<String, Any> StreamCodec
         */
        private val EXTRA_PROPS_STREAM_CODEC: StreamCodec<ByteBuf, Map<String, Any>> = object : StreamCodec<ByteBuf, Map<String, Any>> {
            override fun encode(buffer: ByteBuf, value: Map<String, Any>) {
                ByteBufCodecs.VAR_INT.encode(buffer, value.size)
                value.forEach { (k, v) ->
                    ByteBufCodecs.STRING_UTF8.encode(buffer, k)
                    when (v) {
                        is String -> {
                            buffer.writeByte(TYPE_STRING.toInt())
                            ByteBufCodecs.STRING_UTF8.encode(buffer, v)
                        }
                        is Boolean -> {
                            buffer.writeByte(TYPE_BOOLEAN.toInt())
                            buffer.writeBoolean(v)
                        }
                        is Int -> {
                            buffer.writeByte(TYPE_INT.toInt())
                            buffer.writeInt(v)
                        }
                        else -> {
                            // 回退到字符串
                            buffer.writeByte(TYPE_STRING.toInt())
                            ByteBufCodecs.STRING_UTF8.encode(buffer, v.toString())
                        }
                    }
                }
            }

            override fun decode(buffer: ByteBuf): Map<String, Any> {
                val size = ByteBufCodecs.VAR_INT.decode(buffer)
                val map = LinkedHashMap<String, Any>(size)
                repeat(size) {
                    val key = ByteBufCodecs.STRING_UTF8.decode(buffer)
                    when (buffer.readByte()) {
                        TYPE_STRING -> map[key] = ByteBufCodecs.STRING_UTF8.decode(buffer)
                        TYPE_BOOLEAN -> map[key] = buffer.readBoolean()
                        TYPE_INT -> map[key] = buffer.readInt()
                        else -> map[key] = ByteBufCodecs.STRING_UTF8.decode(buffer)
                    }
                }
                return map
            }
        }

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, OResourceDependency::id,
            ODependencyType.STREAM_CODEC, OResourceDependency::type,
            ByteBufCodecs.STRING_UTF8, OResourceDependency::path,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), { Optional.ofNullable(it.version) },
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), { Optional.ofNullable(it.description) },
            EXTRA_PROPS_STREAM_CODEC, { it.extraProps }
        ) { id, type, path, version, description, extraProps ->
            OResourceDependency(
                id = id,
                type = type,
                path = path,
                version = version.orElse(null),
                description = description.orElse(null),
                extraProps = extraProps,
            )
        }
        
        /**
         * List codec for JSON serialization
         */
        @JvmStatic
        val LIST_CODEC: Codec<List<OResourceDependency>> = CODEC.listOf()

        /**
         * List StreamCodec for network transmission
         */
        @JvmStatic
        val LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.list())
        
        /**
         * 静态存储 - 遵循 ORIGINS 模式
         */
        @JvmStatic
        val ORIGINS = linkedMapOf<ResourceLocation, List<OResourceDependency>>()
        
        /**
         * ORIGINS映射的StreamCodec，用于网络传输
         */
        @JvmStatic
        val ORIGIN_MAP_STREAM_CODEC = ByteBufCodecs.map(
            ::LinkedHashMap,
            ResourceLocation.STREAM_CODEC,
            LIST_STREAM_CODEC
        )
        
        /**
         * 获取资源的依赖列表
         */
        @JvmStatic
        fun get(resourceLocation: ResourceLocation): List<OResourceDependency>? = ORIGINS[resourceLocation]
        
        /**
         * 注册资源依赖
         */
        @JvmStatic
        fun register(resourceLocation: ResourceLocation, dependencies: List<OResourceDependency>) {
            ORIGINS[resourceLocation] = dependencies
        }
    }
}

/**
 * 依赖类型
 * 统一的依赖类型定义，支持 Mojang Codec 序列化
 */
enum class ODependencyType {
    /** 硬依赖 - 必须存在，否则资源加载失败 */
    HARD,
    
    /** 软依赖 - 推荐存在，缺失时只记录警告 */
    SOFT,
    
    /** 可选依赖 - 完全可选，用于增强功能 */
    OPTIONAL;
    
    companion object {
        /**
         * Mojang Codec for ODependencyType
         */
        @JvmStatic
        val CODEC: Codec<ODependencyType> = Codec.STRING.xmap(
            { type -> valueOf(type.uppercase()) },
            { it.name.lowercase() }
        )
        
        /**
         * Stream codec for network serialization
         */
        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, ODependencyType> {
            override fun decode(buffer: FriendlyByteBuf): ODependencyType {
                return buffer.readEnum(ODependencyType::class.java)
            }
            override fun encode(buffer: FriendlyByteBuf, value: ODependencyType) {
                buffer.writeEnum(value)
            }
        }
    }
}
