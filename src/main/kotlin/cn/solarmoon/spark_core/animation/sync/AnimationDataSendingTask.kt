package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
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
 * 配置任务，用于在新玩家加入时向其发送全量的动画数据。
 * 写法仿照 ModelDataSendingTask.kt。
 */
class AnimationDataSendingTask : ICustomConfigurationTask {

    override fun run(sender: Consumer<CustomPacketPayload?>) {
        SparkCore.LOGGER.info("服务器端 AnimationDataSendingTask: 准备向新玩家发送全量 OAnimationSet 数据...")
        
        // 从 SparkRegistries.TYPED_ANIMATION 动态注册表获取数据，确保与增量同步数据源一致
        val animationsToSync = LinkedHashMap<ResourceLocation, OAnimationSet>()
        
        SparkRegistries.TYPED_ANIMATION?.let { registry ->
            registry.entrySet().forEach { entry ->
                val registryKey = entry.key
                val typedAnimation = entry.value
                val animationSetLocation = typedAnimation.index.index
                
                // 从 TypedAnimation 提取对应的 OAnimationSet
                val animationSet = convertTypedAnimationToOAnimationSet(typedAnimation)
                animationsToSync[animationSetLocation] = animationSet
                
                SparkCore.LOGGER.debug("从动态注册表获取 TypedAnimation: ${registryKey.location()} -> 动画集: $animationSetLocation")
            }
        } ?: run {
            SparkCore.LOGGER.warn("TYPED_ANIMATION 动态注册表为空，将发送空的动画数据")
        }

        if (animationsToSync.isNotEmpty()) {
            // AnimationDataSyncPayload 的构造函数现在只接受 animations
            val payload = AnimationDataSyncPayload(animationsToSync)
            sender.accept(payload)
            SparkCore.LOGGER.info("成功向新玩家发送了包含 ${animationsToSync.size} 个根 OAnimationSet 的同步包（来源：动态注册表）。等待客户端确认...")
        } else {
            SparkCore.LOGGER.info("动态注册表中没有加载任何 TypedAnimation 数据，将向新玩家发送空的同步包。")
            // 发送一个包含空 map 的 payload
            val payload = AnimationDataSyncPayload(LinkedHashMap()) // 空的 LinkedHashMap
            sender.accept(payload)
        }
    }
    
    /**
     * 将 TypedAnimation 转换为 OAnimationSet
     * 通过 TypedAnimation 的 index.index (ResourceLocation) 从静态 ORIGINS 获取 OAnimationSet
     */
    private fun convertTypedAnimationToOAnimationSet(typedAnimation: cn.solarmoon.spark_core.animation.anim.play.TypedAnimation): OAnimationSet {
        val animationSetLocation = typedAnimation.index.index
        return OAnimationSet.ORIGINS[animationSetLocation] ?: OAnimationSet.EMPTY
    }

    override fun type(): ConfigurationTask.Type {
        return TYPE
    }

    companion object {
        @JvmStatic
        val TYPE = ConfigurationTask.Type(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "config/animation_data_sending_task"))
    }

    /**
     * 客户端在接收并处理完 AnimationDataSyncPayload 后，发送此 Payload 给服务器以确认。
     */
    data class AckPayload(val receivedCount: Int) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmStatic
            val TYPE = CustomPacketPayload.Type<AckPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "config/animation_data_ack"))

            @JvmStatic
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AckPayload> = StreamCodec.composite(
                ByteBufCodecs.INT, AckPayload::receivedCount,
                ::AckPayload
            )

            @JvmStatic
            fun handleOnServer(payload: AckPayload, context: IPayloadContext) {
                SparkCore.LOGGER.info("服务器收到 AnimationDataSendingTask.AckPayload，客户端已接收 ${payload.receivedCount} 个动画集。完成配置任务。")
                context.finishCurrentTask(AnimationDataSendingTask.TYPE) // 使用父任务的 TYPE
            }
        }
    }
}
