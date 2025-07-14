package cn.solarmoon.spark_core.resource.origin

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

/**
 * 依赖集合数据类
 */
data class ODependencySet(
    val dependencies: LinkedHashMap<String, List<OResourceDependency>>
) {
    
    /**
     * 获取指定资源的依赖列表
     */
    fun getDependencies(resourceKey: String): List<OResourceDependency> {
        return dependencies[resourceKey] ?: emptyList()
    }
    
    /**
     * 检查是否包含指定资源的依赖
     */
    fun hasDependencies(resourceKey: String): Boolean {
        return dependencies.containsKey(resourceKey) && dependencies[resourceKey]?.isNotEmpty() == true
    }
    
    /**
     * 获取依赖总数
     */
    fun getTotalDependencyCount(): Int {
        return dependencies.values.sumOf { it.size }
    }
    
    /**
     * 获取所有资源键
     */
    fun getAllResourceKeys(): Set<String> {
        return dependencies.keys.toSet()
    }
    
    companion object {
        /**
         * 获取依赖集合
         * 仿照 OModel 和 OAnimationSet 的 get 方法
         */
        @JvmStatic
        fun get(res: ResourceLocation): ODependencySet = ORIGINS[res] ?: EMPTY
        
        /**
         * 静态存储 - 仿照 OModel.ORIGINS 模式
         */
        @JvmStatic
        val ORIGINS = linkedMapOf<ResourceLocation, ODependencySet>()
        
        /**
         * 空的依赖集合
         */
        @JvmStatic
        val EMPTY get() = ODependencySet(linkedMapOf())
        
        /**
         * Mojang Codec for ODependencySet
         */
        @JvmStatic
        val CODEC: Codec<ODependencySet> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.unboundedMap(Codec.STRING, OResourceDependency.LIST_CODEC)
                    .xmap({ LinkedHashMap(it) }, { it })
                    .fieldOf("dependencies")
                    .forGetter(ODependencySet::dependencies)
            ).apply(instance, ::ODependencySet)
        }
        
        /**
         * Stream codec for network serialization
         */
        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, ODependencySet> {
            override fun decode(buffer: FriendlyByteBuf): ODependencySet {
                val size = buffer.readVarInt()
                val dependencies = LinkedHashMap<String, List<OResourceDependency>>()
                
                for (i in 0 until size) {
                    val key = buffer.readUtf()
                    val deps = OResourceDependency.LIST_STREAM_CODEC.decode(buffer)
                    dependencies[key] = deps
                }
                
                return ODependencySet(dependencies)
            }

            override fun encode(buffer: FriendlyByteBuf, value: ODependencySet) {
                buffer.writeVarInt(value.dependencies.size)
                
                for ((key, deps) in value.dependencies) {
                    buffer.writeUtf(key)
                    OResourceDependency.LIST_STREAM_CODEC.encode(buffer, deps)
                }
            }
        }
        
        /**
         * Origins map stream codec for network synchronization
         */
        @JvmStatic
        val ORIGIN_MAP_STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, LinkedHashMap<ResourceLocation, ODependencySet>> {
            override fun decode(buffer: FriendlyByteBuf): LinkedHashMap<ResourceLocation, ODependencySet> {
                val size = buffer.readVarInt()
                val map = LinkedHashMap<ResourceLocation, ODependencySet>()
                
                for (i in 0 until size) {
                    val location = ResourceLocation.STREAM_CODEC.decode(buffer)
                    val dependencySet = STREAM_CODEC.decode(buffer)
                    map[location] = dependencySet
                }
                
                return map
            }

            override fun encode(buffer: FriendlyByteBuf, value: LinkedHashMap<ResourceLocation, ODependencySet>) {
                buffer.writeVarInt(value.size)
                
                for ((location, dependencySet) in value) {
                    ResourceLocation.STREAM_CODEC.encode(buffer, location)
                    STREAM_CODEC.encode(buffer, dependencySet)
                }
            }
        }
    }
}