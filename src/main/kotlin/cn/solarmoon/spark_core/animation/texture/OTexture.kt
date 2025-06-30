package cn.solarmoon.spark_core.animation.texture

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

/**
 * 动态纹理数据类
 * 用于动态注册表管理
 */
data class OTexture(
    val location: ResourceLocation,   // 纹理位置标识
    val textureData: ByteArray,      // 纹理二进制数据
    val width: Int,                  // 纹理宽度
    val height: Int                  // 纹理高度
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OTexture

        if (location != other.location) return false
        if (!textureData.contentEquals(other.textureData)) return false
        if (width != other.width) return false
        if (height != other.height) return false

        return true
    }

    override fun hashCode(): Int {
        var result = location.hashCode()
        result = 31 * result + textureData.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }

    companion object {
        
        /**
         * 获取纹理。
         * 统一数据访问优先级：SparkRegistries动态注册表 > 静态ORIGINS
         * 确保客户端和服务端数据一致性
         */
        @JvmStatic
        fun get(res: ResourceLocation): OTexture? {
            // 优先从动态注册表获取
            SparkRegistries.DYNAMIC_TEXTURES?.let { registry ->
                val resourceKey = net.minecraft.resources.ResourceKey.create(registry.key(), res)
                registry.get(resourceKey)?.let { return it }
            }
            
            // 回退到静态ORIGINS
            return ORIGINS[res]
        }

        /**
         * 获取所有已注册的纹理
         */
        @JvmStatic
        fun getAllTextures(): Map<ResourceLocation, OTexture> {
            val result = mutableMapOf<ResourceLocation, OTexture>()
            
            // 从动态注册表获取
            SparkRegistries.DYNAMIC_TEXTURES?.let { registry ->
                registry.entrySet().forEach { entry ->
                    result[entry.key.location()] = entry.value
                }
            }
            
            // 如果动态注册表为空，从静态ORIGINS获取
            if (result.isEmpty()) {
                result.putAll(ORIGINS)
            }
            
            return result
        }

        /**
         * 检查纹理是否存在
         */
        @JvmStatic
        fun exists(res: ResourceLocation): Boolean {
            // 优先检查动态注册表
            SparkRegistries.DYNAMIC_TEXTURES?.let { registry ->
                val resourceKey = net.minecraft.resources.ResourceKey.create(registry.key(), res)
                if (registry.get(resourceKey) != null) return true
            }
            
            // 回退到静态ORIGINS
            return ORIGINS.containsKey(res)
        }

        @JvmStatic
        var ORIGINS = linkedMapOf<ResourceLocation, OTexture>()

        @JvmField
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, OTexture> {
            override fun encode(buf: FriendlyByteBuf, texture: OTexture) {
                buf.writeResourceLocation(texture.location)
                ByteBufCodecs.BYTE_ARRAY.encode(buf, texture.textureData)
                buf.writeInt(texture.width)
                buf.writeInt(texture.height)
            }

            override fun decode(buf: FriendlyByteBuf): OTexture {
                val location = buf.readResourceLocation()
                val textureData = ByteBufCodecs.BYTE_ARRAY.decode(buf)
                val width = buf.readInt()
                val height = buf.readInt()
                return OTexture(location, textureData, width, height)
            }
        }
    }
}