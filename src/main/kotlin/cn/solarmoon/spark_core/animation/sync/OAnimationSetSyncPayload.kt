package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.client.ClientAnimationDataManager
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * 用于在资源热重载等情况下，同步单个 OAnimationSet 到客户端的 Payload。
 * @param rootLocation OAnimationSet 的根 ResourceLocation。
 * @param animationSet 要同步的 OAnimationSet 对象。
 */
data class OAnimationSetSyncPayload(
    val rootLocation: ResourceLocation,
    val animationSet: OAnimationSet
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<OAnimationSetSyncPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<OAnimationSetSyncPayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "o_animation_set_sync")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, OAnimationSetSyncPayload> = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, OAnimationSetSyncPayload::rootLocation,
            OAnimationSet.STREAM_CODEC, OAnimationSetSyncPayload::animationSet,
            ::OAnimationSetSyncPayload
        )

        @JvmStatic
        fun handleInClient(payload: OAnimationSetSyncPayload, context: IPayloadContext) {
            context.enqueueWork {
                SparkCore.LOGGER.info("Received OAnimationSetSyncPayload for: ${payload.rootLocation}. Updating ClientAnimationDataManager.")
                ClientAnimationDataManager.updateAnimationSet(payload.rootLocation, payload.animationSet)
                // 日志已在 ClientAnimationDataManager.updateAnimationSet 中记录
                // SparkCore.LOGGER.info("ClientAnimationDataManager updated OAnimationSet for: ${payload.rootLocation}")
                // TODO: 考虑是否需要进一步的客户端刷新逻辑，例如针对使用这些动画的实体进行特定更新。
            }.exceptionally { e ->
                SparkCore.LOGGER.error("Error processing OAnimationSetSyncPayload for ${payload.rootLocation} on client", e)
                null
            }
        }
    }
}
