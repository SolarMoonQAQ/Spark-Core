package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
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
         * 在客户端处理此Payload的逻辑，直接操作OAnimationSet.ORIGINS与新的handle机制保持一致
         */
        @JvmStatic
        fun handleInClient(payload: AnimationDataSyncPayload, context: IPayloadContext) {
            context.enqueueWork {
                SparkCore.LOGGER.info("Received Full AnimationDataSyncPayload on client. Animations count: ${payload.animations.size}")

                // 直接操作 OAnimationSet.ORIGINS，与新的 handle 机制保持一致
                OAnimationSet.ORIGINS.clear()
                // 同时清理AnimIndex.ORIGINS
                //AnimIndex.ORIGINS.clear()

                payload.animations.forEach { (location, animationSet) ->
                    OAnimationSet.ORIGINS[location] = animationSet
                    SparkCore.LOGGER.debug("Added/Replaced animation set for: {} with OAnimationSet object", location)

                    // 重建AnimIndex.ORIGINS映射
                    val pathParts = location.path.split("/")
                    if (pathParts.size >= 3) {
                        val entityPath = pathParts[2]
                        animationSet.animations.keys.forEach { animName ->
                            val shortcutPath = ResourceLocation.fromNamespaceAndPath(
                                "minecraft",
                                "$entityPath/$animName"
                            )
                            //AnimIndex.ORIGINS[shortcutPath] = location
                        }
                    }
                }

                //SparkCore.LOGGER.info("Rebuilt AnimIndex.ORIGINS with {} shortcuts", AnimIndex.ORIGINS.size)
                
                SparkCore.LOGGER.info("OAnimationSet.ORIGINS updated with ${payload.animations.size} OAnimationSet objects.")
                
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
