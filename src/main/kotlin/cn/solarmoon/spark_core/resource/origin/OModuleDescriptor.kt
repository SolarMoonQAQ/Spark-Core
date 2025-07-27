package cn.solarmoon.spark_core.resource.origin

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import java.util.Optional

/**
 * 模块描述器
 */
data class OModuleDescriptor(
    /** 模块唯一标识符，必须全局唯一 */
    val id: String,
    
    /** 模块版本，推荐使用语义化版本（SemVer） */
    val version: String,
    
    /** 模块简短描述 */
    val description: String? = null,
    
    /** 模块作者或组织 */
    val author: String? = null,
    
    /** 强依赖列表，支持版本约束语法 */
    val depends: List<String> = emptyList(),
    
    /** 软依赖/推荐依赖列表 */
    val recommends: List<String> = emptyList(),
    
    /** 提供的虚拟特性列表 */
    val provides: List<String> = emptyList(),
    
    /** 冲突模块列表 */
    val conflicts: List<String> = emptyList(),
    
    /** 是否为付费内容（为未来功能预留） */
    val paid: Boolean = false
) {
    
    companion object {
        
        /**
         * 获取模块描述器
         * 仿照 OModel 和 OAnimationSet 的 get 方法
         */
        @JvmStatic
        fun get(res: ResourceLocation): OModuleDescriptor? = ORIGINS[res]
        
        /**
         * 静态存储 - 仿照 OModel.ORIGINS 模式
         */
        @JvmStatic
        val ORIGINS = linkedMapOf<ResourceLocation, OModuleDescriptor>()

        
        /**
         * Mojang Codec for OModuleDescriptor
         */
        @JvmStatic
        val CODEC: Codec<OModuleDescriptor> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter(OModuleDescriptor::id),
                Codec.STRING.fieldOf("version").forGetter(OModuleDescriptor::version),
                Codec.STRING.optionalFieldOf("description").forGetter { Optional.ofNullable(it.description) },
                Codec.STRING.optionalFieldOf("author").forGetter { Optional.ofNullable(it.author) },
                Codec.STRING.listOf().optionalFieldOf("depends", emptyList()).forGetter(OModuleDescriptor::depends),
                Codec.STRING.listOf().optionalFieldOf("recommends", emptyList()).forGetter(OModuleDescriptor::recommends),
                Codec.STRING.listOf().optionalFieldOf("provides", emptyList()).forGetter(OModuleDescriptor::provides),
                Codec.STRING.listOf().optionalFieldOf("conflicts", emptyList()).forGetter(OModuleDescriptor::conflicts),
                Codec.BOOL.optionalFieldOf("paid", false).forGetter(OModuleDescriptor::paid)
            ).apply(instance) { id, version, description, author, depends, recommends, provides, conflicts, paid ->
                OModuleDescriptor(
                    id = id,
                    version = version,
                    description = description.orElse(null),
                    author = author.orElse(null),
                    depends = depends,
                    recommends = recommends,
                    provides = provides,
                    conflicts = conflicts,
                    paid = paid
                )
            }
        }
        
        /**
         * Stream codec for network serialization
         */
        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, OModuleDescriptor> {
            override fun decode(buffer: FriendlyByteBuf): OModuleDescriptor {
                val id = buffer.readUtf()
                val version = buffer.readUtf()
                val description = if (buffer.readBoolean()) buffer.readUtf() else null
                val author = if (buffer.readBoolean()) buffer.readUtf() else null
                val depends = readStringList(buffer)
                val recommends = readStringList(buffer)
                val provides = readStringList(buffer)
                val conflicts = readStringList(buffer)
                val paid = buffer.readBoolean()
                
                return OModuleDescriptor(
                    id = id,
                    version = version,
                    description = description,
                    author = author,
                    depends = depends,
                    recommends = recommends,
                    provides = provides,
                    conflicts = conflicts,
                    paid = paid
                )
            }

            override fun encode(buffer: FriendlyByteBuf, value: OModuleDescriptor) {
                buffer.writeUtf(value.id)
                buffer.writeUtf(value.version)
                
                buffer.writeBoolean(value.description != null)
                value.description?.let { buffer.writeUtf(it) }
                
                buffer.writeBoolean(value.author != null)
                value.author?.let { buffer.writeUtf(it) }
                
                writeStringList(buffer, value.depends)
                writeStringList(buffer, value.recommends)
                writeStringList(buffer, value.provides)
                writeStringList(buffer, value.conflicts)
                
                buffer.writeBoolean(value.paid)
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
        
        /**
         * Origins map stream codec for network synchronization
         */
        @JvmStatic
        val ORIGIN_MAP_STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, LinkedHashMap<ResourceLocation, OModuleDescriptor>> {
            override fun decode(buffer: FriendlyByteBuf): LinkedHashMap<ResourceLocation, OModuleDescriptor> {
                val size = buffer.readVarInt()
                val map = LinkedHashMap<ResourceLocation, OModuleDescriptor>()
                
                for (i in 0 until size) {
                    val location = ResourceLocation.STREAM_CODEC.decode(buffer)
                    val descriptor = STREAM_CODEC.decode(buffer)
                    map[location] = descriptor
                }
                
                return map
            }

            override fun encode(buffer: FriendlyByteBuf, value: LinkedHashMap<ResourceLocation, OModuleDescriptor>) {
                buffer.writeVarInt(value.size)
                
                for ((location, descriptor) in value) {
                    ResourceLocation.STREAM_CODEC.encode(buffer, location)
                    STREAM_CODEC.encode(buffer, descriptor)
                }
            }
        }
    }
}