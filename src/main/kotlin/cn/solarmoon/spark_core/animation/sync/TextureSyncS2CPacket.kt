package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.payload.resource_sync.ChangeType
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO



data class TextureSyncS2CPacket(
    val textureLocation: ResourceLocation,
    val operationType: ChangeType,
    val textureData: ByteArray = ByteArray(0)
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TextureSyncS2CPacket

        if (textureLocation != other.textureLocation) return false
        if (operationType != other.operationType) return false
        if (!textureData.contentEquals(other.textureData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = textureLocation.hashCode()
        result = 31 * result + operationType.hashCode()
        result = 31 * result + textureData.contentHashCode()
        return result
    }

    companion object {
        @JvmField
        val ID = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "texture_sync")

        @JvmField
        val TYPE = CustomPacketPayload.Type<TextureSyncS2CPacket>(ID)

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, TextureSyncS2CPacket> = StreamCodec.of(
            // 编码: (缓冲区, 数据包) -> Unit - 注意参数顺序！
            { buffer, packet -> encode(packet, buffer) },
            // 解码: (缓冲区) -> 数据包
            { buffer -> decode(buffer) }
        )

        /**
         * 将数据包编码到缓冲区
         * @param packet 要编码的纹理同步数据包
         * @param buffer 目标缓冲区
         */
        private fun encode(packet: TextureSyncS2CPacket, buffer: FriendlyByteBuf) {
            ResourceLocation.STREAM_CODEC.encode(buffer, packet.textureLocation)
            ByteBufCodecs.VAR_INT.encode(buffer, packet.operationType.ordinal)
            ByteBufCodecs.BYTE_ARRAY.encode(buffer, packet.textureData)
        }

        /**
         * 从缓冲区解码数据包
         * @param buffer 源缓冲区
         * @return 解码得到的纹理同步数据包
         */
        private fun decode(buffer: FriendlyByteBuf): TextureSyncS2CPacket {
            val textureLocation = ResourceLocation.STREAM_CODEC.decode(buffer)
            val operationType = ChangeType.entries[ByteBufCodecs.VAR_INT.decode(buffer)]
            val textureData = ByteBufCodecs.BYTE_ARRAY.decode(buffer)
            return TextureSyncS2CPacket(textureLocation, operationType, textureData)
        }

        /**
         * 在客户端处理纹理同步数据包
         */
        @JvmStatic
        fun handleInClient(packet: TextureSyncS2CPacket, context: IPayloadContext) {
            context.enqueueWork {
                try {
                    when (packet.operationType) {
                        ChangeType.ADDED -> {
                            loadTextureFromBytes(packet.textureLocation, packet.textureData)
                            SparkCore.LOGGER.info("客户端成功加载同步纹理: {}", packet.textureLocation)
                        }
                        ChangeType.REMOVED -> {
                            unloadTexture(packet.textureLocation)
                            SparkCore.LOGGER.info("客户端成功移除同步纹理: {}", packet.textureLocation)
                        }
                        ChangeType.MODIFIED -> {
                            loadTextureFromBytes(packet.textureLocation, packet.textureData)
                            SparkCore.LOGGER.info("客户端成功更新同步纹理: {}", packet.textureLocation)
                        }
                    }
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("处理纹理同步数据包时发生错误: {}", packet.textureLocation, e)
                }
            }
        }

        /**
         * 从字节数组加载纹理到客户端
         */
        private fun loadTextureFromBytes(location: ResourceLocation, data: ByteArray) {
            if (data.isEmpty()) {
                SparkCore.LOGGER.warn("纹理数据为空，跳过加载: {}", location)
                return
            }

            try {
                ByteArrayInputStream(data).use { inputStream ->
                    val nativeImage = NativeImage.read(inputStream)
                    // 确保我们在客户端
                    if (Minecraft.getInstance().level != null) {
                        val dynamicTexture = DynamicTexture(nativeImage)
                        val textureManager = Minecraft.getInstance().textureManager
                        
                        // 先移除旧纹理（如果存在）
                        try {
                            textureManager.release(location)
                        } catch (e: Exception) {
                            // 忽略，纹理可能不存在
                        }
                        
                        // 注册新纹理
                        textureManager.register(location, dynamicTexture)
                        SparkCore.LOGGER.debug("从字节数组成功加载纹理: {}", location)
                    } else {
                        nativeImage.close() // 释放资源
                        SparkCore.LOGGER.warn("客户端世界未加载，跳过纹理注册: {}", location)
                    }
                }
            } catch (e: IOException) {
                SparkCore.LOGGER.error("加载纹理时发生IO错误: {}", location, e)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("加载纹理时发生未知错误: {}", location, e)
            }
        }

        /**
         * 卸载纹理
         */
        private fun unloadTexture(location: ResourceLocation) {
            try {
                val textureManager = Minecraft.getInstance().textureManager
                
                // 使用Minecraft的缺失纹理作为占位符
                val missingTextureLocation = MissingTextureAtlasSprite.getLocation()
                val missingTexture = textureManager.getTexture(missingTextureLocation)
                
                // 先release旧纹理
                textureManager.release(location)
                // 然后注册缺失纹理作为占位符
                textureManager.register(location, missingTexture)
                
                SparkCore.LOGGER.debug("纹理已替换为缺失纹理占位符(紫黑格子): {}", location)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("卸载纹理时发生错误: {}", location, e)
                // 出错时直接release
                try {
                    Minecraft.getInstance().textureManager.release(location)
                } catch (re: Exception) {
                    // 忽略二次释放错误
                }
            }
        }

        /**
         * 发送纹理添加同步包到所有客户端
         */
        @JvmStatic
        fun syncTextureAdditionToClients(location: ResourceLocation, textureData: ByteArray) {
            val packet = TextureSyncS2CPacket(location, ChangeType.ADDED, textureData)
            PacketDistributor.sendToAllPlayers(packet)
            SparkCore.LOGGER.info("发送纹理添加同步包到所有客户端: {}, 数据大小: {} 字节", location, textureData.size)
        }

        /**
         * 发送纹理移除同步包到所有客户端
         */
        @JvmStatic
        fun syncTextureRemovalToClients(location: ResourceLocation) {
            val packet = TextureSyncS2CPacket(location, ChangeType.REMOVED)
            PacketDistributor.sendToAllPlayers(packet)
            SparkCore.LOGGER.info("发送纹理移除同步包到所有客户端: {}", location)
        }

        /**
         * 将图像文件转换为字节数组
         */
        @JvmStatic
        fun imageToByteArray(nativeImage: NativeImage): ByteArray {
            return try {
                ByteArrayOutputStream().use { outputStream ->
                    // 将NativeImage转换为BufferedImage再转为字节数组
                    val width = nativeImage.width
                    val height = nativeImage.height
                    val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                    
                    for (x in 0 until width) {
                        for (y in 0 until height) {
                            bufferedImage.setRGB(x, y, nativeImage.getPixelRGBA(x, y))
                        }
                    }
                    
                    ImageIO.write(bufferedImage, "png", outputStream)
                    outputStream.toByteArray()
                }
            } catch (e: Exception) {
                SparkCore.LOGGER.error("转换图像为字节数组时发生错误", e)
                ByteArray(0)
            }
        }
    }
} 