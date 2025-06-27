package cn.solarmoon.spark_core.animation.texture.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.texture.OTexture
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.fml.loading.FMLEnvironment
import java.util.LinkedHashMap
import net.minecraft.network.codec.ByteBufCodecs

/**
 * 用于全量同步纹理数据的Payload
 * @param textures 纹理数据 Map<ResourceLocation, OTexture>
 */
data class TextureDataSyncPayload(
    val textures: LinkedHashMap<ResourceLocation, OTexture>
) : CustomPacketPayload {

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<TextureDataSyncPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "texture_data_full_sync"))

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, TextureDataSyncPayload> = StreamCodec.composite(
            ByteBufCodecs.map(
                ::LinkedHashMap,
                ResourceLocation.STREAM_CODEC,
                OTexture.STREAM_CODEC
            ),
            TextureDataSyncPayload::textures,
            ::TextureDataSyncPayload
        )

        /**
         * 在客户端处理此Payload的逻辑
         */
        @JvmStatic
        fun handleInClient(payload: TextureDataSyncPayload, context: IPayloadContext) {
            context.enqueueWork {
                SparkCore.LOGGER.info("客户端接收到全量纹理同步数据. 纹理数量: ${payload.textures.size}")
                
                try {
                    // 清空客户端动态注册表
                    SparkRegistries.DYNAMIC_TEXTURES?.clearDynamic()
                    
                    // 将全量同步数据注册到客户端动态注册表和TextureManager
                    payload.textures.forEach { (location, texture) ->
                        // 注册到动态注册表
                        val resourceKey = net.minecraft.resources.ResourceKey.create(SparkRegistries.DYNAMIC_TEXTURES!!.key(), location)
                        SparkRegistries.DYNAMIC_TEXTURES!!.register(resourceKey, texture, net.minecraft.core.RegistrationInfo.BUILT_IN)
                        
                        // 在客户端注册到TextureManager
                        if (!FMLEnvironment.dist.isDedicatedServer()) {
                            registerTextureToManager(location, texture)
                        }
                        
                        SparkCore.LOGGER.debug("客户端注册纹理: {} -> 尺寸: {}x{}", location, texture.width, texture.height)
                    }
                    
                    SparkCore.LOGGER.info("客户端动态注册表已更新，包含 ${payload.textures.size} 个纹理")
                    
                    // 发送确认给服务器
                    val ack = TextureDataSendingTask.AckPayload(payload.textures.size)
                    context.reply(ack) 
                    SparkCore.LOGGER.info("已向服务器发送纹理全量同步确认")
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("处理纹理全量同步数据时发生错误", e)
                }
            }.exceptionally { e -> 
                SparkCore.LOGGER.error("客户端处理纹理全量同步时发生异常", e)
                null
            }
        }

        /**
         * 将纹理注册到客户端TextureManager
         */
        private fun registerTextureToManager(location: ResourceLocation, texture: OTexture) {
            try {
                // 将ByteArray转换为NativeImage
                val nativeImage = createNativeImageFromByteArray(texture.textureData, texture.width, texture.height)
                
                // 创建DynamicTexture并注册到TextureManager
                val dynamicTexture = DynamicTexture(nativeImage)
                val textureManager = Minecraft.getInstance().textureManager
                textureManager.register(location, dynamicTexture)
                
                SparkCore.LOGGER.debug("客户端纹理已注册到TextureManager: {}", location)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("注册纹理到TextureManager时发生错误: {}", location, e)
            }
        }

        /**
         * 从ByteArray创建NativeImage
         */
        private fun createNativeImageFromByteArray(data: ByteArray, width: Int, height: Int): NativeImage {
            val nativeImage = NativeImage(width, height, false)
            
            var index = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (index + 3 < data.size) {
                        val r = data[index++].toInt() and 0xFF
                        val g = data[index++].toInt() and 0xFF
                        val b = data[index++].toInt() and 0xFF
                        val a = data[index++].toInt() and 0xFF
                        
                        // 构建ABGR像素值（与NativeImage格式匹配）
                        val pixel = (a shl 24) or (b shl 16) or (g shl 8) or r
                        nativeImage.setPixelRGBA(x, y, pixel)
                    }
                }
            }
            
            return nativeImage
        }
    }

    override fun type(): CustomPacketPayload.Type<*> {
        return TYPE
    }
} 