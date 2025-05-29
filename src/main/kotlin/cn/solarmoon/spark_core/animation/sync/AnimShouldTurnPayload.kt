package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class AnimShouldTurnPayload private constructor(
    val syncerType: SyncerType,
    val syncData: SyncData,
    val shouldTurnHead: Boolean,
    val shouldTurnBody: Boolean
): CustomPacketPayload {
    constructor(animatable: IAnimatable<*>, shouldTurnHead: Boolean, shouldTurnBody: Boolean): this(animatable.syncerType, animatable.syncData, shouldTurnHead, shouldTurnBody)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: AnimShouldTurnPayload, context: IPayloadContext) {
            val level = context.player().level()
            val animatable = payload.syncerType.getSyncer(level, payload.syncData) as? IAnimatable<*> ?: return
            animatable.animController.mainAnim?.let { it.shouldTurnBody = payload.shouldTurnBody }
            animatable.animController.mainAnim?.let { it.shouldTurnHead = payload.shouldTurnHead }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<AnimShouldTurnPayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "anim_should_turn"),
        )

        @JvmStatic
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, AnimShouldTurnPayload> = StreamCodec.composite(
            SyncerType.STREAM_CODEC, AnimShouldTurnPayload::syncerType,
            SyncData.STREAM_CODEC, AnimShouldTurnPayload::syncData,
            ByteBufCodecs.BOOL, AnimShouldTurnPayload::shouldTurnHead,
            ByteBufCodecs.BOOL, AnimShouldTurnPayload::shouldTurnBody,
            ::AnimShouldTurnPayload
        )
    }
}