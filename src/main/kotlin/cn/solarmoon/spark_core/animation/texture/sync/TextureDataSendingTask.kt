package cn.solarmoon.spark_core.animation.texture.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.texture.OTexture
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.network.ConfigurationTask
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.function.Consumer
import net.minecraft.network.codec.ByteBufCodecs
import java.util.LinkedHashMap

/**
 * 配置任务，用于在新玩家加入时向其发送全量的纹理数据。
 */
class TextureDataSendingTask : ICustomConfigurationTask {

    override fun run(sender: Consumer<CustomPacketPayload?>) {
        SparkCore.LOGGER.info("服务器端 TextureDataSendingTask: 准备向新玩家发送全量纹理数据...")
        
        // 从 SparkRegistries.DYNAMIC_TEXTURES 动态注册表获取数据，确保与增量同步数据源一致
        val texturesToSync = LinkedHashMap<ResourceLocation, OTexture>()
        
        SparkRegistries.DYNAMIC_TEXTURES?.let { registry ->
            registry.entrySet().forEach { entry ->
                val registryKey = entry.key
                val texture = entry.value
                texturesToSync[registryKey.location()] = texture
                
                SparkCore.LOGGER.debug("从动态注册表获取纹理: ${registryKey.location()} -> 尺寸: ${texture.width}x${texture.height}")
            }
        } ?: run {
            SparkCore.LOGGER.warn("DYNAMIC_TEXTURES 动态注册表为空，将发送空的纹理数据")
        }

        if (texturesToSync.isNotEmpty()) {
            val payload = TextureDataSyncPayload(texturesToSync)
            sender.accept(payload)
            SparkCore.LOGGER.info("成功向新玩家发送了包含 ${texturesToSync.size} 个纹理的同步包（来源：动态注册表）。等待客户端确认...")
        } else {
            SparkCore.LOGGER.info("动态注册表中没有加载任何纹理数据，将向新玩家发送空的同步包。")
            val payload = TextureDataSyncPayload(LinkedHashMap())
            sender.accept(payload)
        }
    }

    override fun type(): ConfigurationTask.Type {
        return TYPE
    }

    companion object {
        @JvmStatic
        val TYPE = ConfigurationTask.Type(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "config/texture_data_sending_task"))
    }

    /**
     * 客户端在接收并处理完 TextureDataSyncPayload 后，发送此 Payload 给服务器以确认。
     */
    data class AckPayload(val receivedCount: Int) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmStatic
            val TYPE = CustomPacketPayload.Type<AckPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "config/texture_data_ack"))

            @JvmStatic
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AckPayload> = StreamCodec.composite(
                ByteBufCodecs.INT, AckPayload::receivedCount,
                ::AckPayload
            )

            @JvmStatic
            fun handleOnServer(payload: AckPayload, context: IPayloadContext) {
                SparkCore.LOGGER.info("服务器收到 TextureDataSendingTask.AckPayload，客户端已接收 ${payload.receivedCount} 个纹理。完成配置任务。")
                context.finishCurrentTask(TextureDataSendingTask.TYPE)
            }
        }
    }
} 