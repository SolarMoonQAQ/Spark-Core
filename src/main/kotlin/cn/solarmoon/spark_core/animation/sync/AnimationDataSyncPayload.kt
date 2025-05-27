package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.client.ClientAnimationDataManager
import cn.solarmoon.spark_core.animation.sync.AnimationDataSendingTask
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.LinkedHashMap

/**
 * 用于全量同步动画数据的Payload，直接包含 OAnimationSet 对象。
 * @param animations 动画数据 Map<ResourceLocation, OAnimationSet>
 */
data class AnimationDataSyncPayload(
    val animations: LinkedHashMap<ResourceLocation, OAnimationSet>
) : CustomPacketPayload {

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<AnimationDataSyncPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "animation_data_full_sync"))

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AnimationDataSyncPayload> = StreamCodec.composite(
            OAnimationSet.ORIGIN_MAP_STREAM_CODEC,
            AnimationDataSyncPayload::animations,
            ::AnimationDataSyncPayload
        )

        /**
         * 在客户端处理此Payload的逻辑，仿照 ModelDataPayload.handleInClient
         */
        @JvmStatic
        fun handleInClient(payload: AnimationDataSyncPayload, context: IPayloadContext) {
            context.enqueueWork {
                SparkCore.LOGGER.info("Received Full AnimationDataSyncPayload on client. Animations count: ${payload.animations.size}")
                
                // 调用 ClientAnimationDataManager 来更新客户端数据
                ClientAnimationDataManager.replaceAllAnimationSetsFromObjects(payload.animations)
                
                SparkCore.LOGGER.info("ClientAnimationDataManager updated with ${payload.animations.size} OAnimationSet objects.")
                
                val ack = AnimationDataSendingTask.AckPayload(payload.animations.size)
                context.reply(ack) 
                SparkCore.LOGGER.info("Sent AnimationDataSendingTask.AckPayload to server after full animation sync.")
            }.exceptionally { e -> 
                SparkCore.LOGGER.error("Error processing AnimationDataSyncPayload on client", e)
                null
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<*> {
        return TYPE
    }
}
