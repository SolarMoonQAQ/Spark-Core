package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.registry.dynamic.DynamicIdManager
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.network.ConfigurationTask
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.LinkedHashMap
import java.util.function.Consumer

/**
 * 配置任务，用于在新玩家加入时向其发送全量的TypedAnimation数据。
 */
class TypedAnimationDataSendingTask : ICustomConfigurationTask {

    override fun run(sender: Consumer<CustomPacketPayload?>) {
        SparkCore.LOGGER.info("服务器端 TypedAnimationDataSendingTask: 准备向新玩家发送全量TypedAnimation数据...")
        
        // 枚举来自稳定索引（而非遍历注册表）以降低时序耦合
        val animationsToSync = LinkedHashMap<ResourceLocation, TypedAnimationDataSyncPayload.TypedAnimationData>()
        val registry = SparkRegistries.TYPED_ANIMATION
        val registryName = registry.key().location().toString()

        val mappings = DynamicIdManager.getMappingsForRegistry(registryName)
        mappings.forEach { (location, assignedId) ->
            // 从注册表按 key 精确查询（避免依赖 entrySet 遍历时序）
            val key = net.minecraft.resources.ResourceKey.create(registry.key(), location)
            val animation = registry.get(key)
            if (animation == null) {
                SparkCore.LOGGER.warn("TypedAnimation 尚未就绪，跳过: {} (ID: {})", location, assignedId)
                return@forEach
            }

            val animData = TypedAnimationDataSyncPayload.TypedAnimationData(
                animIndex = animation.index,
                assignedId = assignedId
            )
            animationsToSync[location] = animData
            SparkCore.LOGGER.debug("从索引获取 TypedAnimation: {} -> ID: {}", location, assignedId)
        }

        if (animationsToSync.isNotEmpty()) {
            val payload = TypedAnimationDataSyncPayload(animationsToSync)
            sender.accept(payload)
            SparkCore.LOGGER.info("成功向新玩家发送了包含 {} 个TypedAnimation的同步包。等待客户端确认...", animationsToSync.size)
        } else {
            SparkCore.LOGGER.info("动态注册表中没有加载任何TypedAnimation数据，将向新玩家发送空的同步包。")
            val payload = TypedAnimationDataSyncPayload(LinkedHashMap())
            sender.accept(payload)
        }
    }

    override fun type(): ConfigurationTask.Type {
        return TYPE
    }

    companion object {
        @JvmStatic
        val TYPE = ConfigurationTask.Type(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "config/typed_animation_data_sending_task"))
    }

    /**
     * 确认Payload，客户端在接收完毕后向服务端发送。
     */
    data class AckPayload(val receivedCount: Int) : CustomPacketPayload {
        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<AckPayload> = CustomPacketPayload.Type(
                ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "typed_animation_data_ack")
            )
            
            @JvmField
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AckPayload> = StreamCodec.composite(
                ByteBufCodecs.INT, AckPayload::receivedCount,
                ::AckPayload
            )

            /**
             * 服务端处理确认Payload的逻辑
             */
            @JvmStatic
            fun handleOnServer(payload: AckPayload, context: IPayloadContext) {
                SparkCore.LOGGER.info("服务端接收到TypedAnimation同步确认，客户端接收了 {} 个动画", payload.receivedCount)
                context.enqueueWork { context.finishCurrentTask(TypedAnimationDataSendingTask.TYPE) }
            }
        }

        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    }
}
