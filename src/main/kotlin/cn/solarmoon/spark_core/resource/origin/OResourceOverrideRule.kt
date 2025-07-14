package cn.solarmoon.spark_core.resource.origin

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

/**
 * 资源覆盖规则（O类）
 * 可序列化的覆盖规则数据类，用于存储在 .meta.json 文件中
 */
data class OResourceOverrideRule(
    /** 规则唯一ID */
    val id: String,
    
    /** 原始资源位置 */
    val originalResource: ResourceLocation,
    
    /** 覆盖资源位置 */
    val overrideResource: ResourceLocation,
    
    /** 引用类型 */
    val referenceType: String = "HARD", // "HARD" 或 "SOFT"
    
    /** 所属模块ID */
    val moduleId: String,
    
    /** 命名空间 */
    val namespace: String = overrideResource.namespace,
    
    /** 描述信息 */
    val description: String = "",
    
    /** 创建时间 */
    val createdTime: Long = System.currentTimeMillis(),
    
    /** 创建者 */
    val author: String = "system",
    
    /** 是否可逆 */
    val reversible: Boolean = true,
    
    /** 是否自动跟踪依赖 */
    val autoTrackDependencies: Boolean = true,
    
    /** 额外属性 */
    val properties: Map<String, String> = emptyMap()
) {
    
    companion object {
        
        /**
         * Mojang Codec for OResourceOverrideRule
         */
        @JvmStatic
        val CODEC: Codec<OResourceOverrideRule> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter(OResourceOverrideRule::id),
                ResourceLocation.CODEC.fieldOf("originalResource").forGetter(OResourceOverrideRule::originalResource),
                ResourceLocation.CODEC.fieldOf("overrideResource").forGetter(OResourceOverrideRule::overrideResource),
                Codec.STRING.optionalFieldOf("referenceType", "HARD").forGetter(OResourceOverrideRule::referenceType),
                Codec.STRING.fieldOf("moduleId").forGetter(OResourceOverrideRule::moduleId),
                Codec.STRING.optionalFieldOf("namespace", "").forGetter(OResourceOverrideRule::namespace),
                Codec.STRING.optionalFieldOf("description", "").forGetter(OResourceOverrideRule::description),
                Codec.LONG.optionalFieldOf("createdTime", System.currentTimeMillis()).forGetter(OResourceOverrideRule::createdTime),
                Codec.STRING.optionalFieldOf("author", "system").forGetter(OResourceOverrideRule::author),
                Codec.BOOL.optionalFieldOf("reversible", true).forGetter(OResourceOverrideRule::reversible),
                Codec.BOOL.optionalFieldOf("autoTrackDependencies", true).forGetter(OResourceOverrideRule::autoTrackDependencies),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("properties", emptyMap()).forGetter(OResourceOverrideRule::properties)
            ).apply(instance, ::OResourceOverrideRule)
        }
        

        @JvmStatic
        val LIST_CODEC: Codec<List<OResourceOverrideRule>> = CODEC.listOf()

        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, OResourceOverrideRule> {
            override fun decode(buffer: FriendlyByteBuf): OResourceOverrideRule {
                val id = buffer.readUtf()
                val originalResource = ResourceLocation.STREAM_CODEC.decode(buffer)
                val overrideResource = ResourceLocation.STREAM_CODEC.decode(buffer)
                val referenceType = buffer.readUtf()
                val moduleId = buffer.readUtf()
                val namespace = buffer.readUtf()
                val description = buffer.readUtf()
                val createdTime = buffer.readLong()
                val author = buffer.readUtf()
                val reversible = buffer.readBoolean()
                val autoTrackDependencies = buffer.readBoolean()
                val properties = readStringMap(buffer)
                
                return OResourceOverrideRule(
                    id = id,
                    originalResource = originalResource,
                    overrideResource = overrideResource,
                    referenceType = referenceType,
                    moduleId = moduleId,
                    namespace = namespace,
                    description = description,
                    createdTime = createdTime,
                    author = author,
                    reversible = reversible,
                    autoTrackDependencies = autoTrackDependencies,
                    properties = properties
                )
            }
            
            override fun encode(buffer: FriendlyByteBuf, value: OResourceOverrideRule) {
                buffer.writeUtf(value.id)
                ResourceLocation.STREAM_CODEC.encode(buffer, value.originalResource)
                ResourceLocation.STREAM_CODEC.encode(buffer, value.overrideResource)
                buffer.writeUtf(value.referenceType)
                buffer.writeUtf(value.moduleId)
                buffer.writeUtf(value.namespace)
                buffer.writeUtf(value.description)
                buffer.writeLong(value.createdTime)
                buffer.writeUtf(value.author)
                buffer.writeBoolean(value.reversible)
                buffer.writeBoolean(value.autoTrackDependencies)
                writeStringMap(buffer, value.properties)
            }
            
            private fun readStringMap(buffer: FriendlyByteBuf): Map<String, String> {
                val size = buffer.readVarInt()
                val map = mutableMapOf<String, String>()
                for (i in 0 until size) {
                    val key = buffer.readUtf()
                    val value = buffer.readUtf()
                    map[key] = value
                }
                return map
            }
            
            private fun writeStringMap(buffer: FriendlyByteBuf, map: Map<String, String>) {
                buffer.writeVarInt(map.size)
                for ((key, value) in map) {
                    buffer.writeUtf(key)
                    buffer.writeUtf(value)
                }
            }
        }

        @JvmStatic
        val LIST_STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, List<OResourceOverrideRule>> {
            override fun decode(buffer: FriendlyByteBuf): List<OResourceOverrideRule> {
                val size = buffer.readVarInt()
                val list = mutableListOf<OResourceOverrideRule>()
                for (i in 0 until size) {
                    list.add(STREAM_CODEC.decode(buffer))
                }
                return list
            }
            
            override fun encode(buffer: FriendlyByteBuf, value: List<OResourceOverrideRule>) {
                buffer.writeVarInt(value.size)
                for (rule in value) {
                    STREAM_CODEC.encode(buffer, rule)
                }
            }
        }
    }
} 