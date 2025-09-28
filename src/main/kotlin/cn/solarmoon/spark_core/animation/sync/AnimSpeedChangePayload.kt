package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.Syncer
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class AnimSpeedChangePayload private constructor(
    val syncerType: SyncerType,
    val syncData: SyncData,
    val time: Int,
    val speed: Double
): CustomPacketPayload {
    constructor(animatable: Syncer, time: Int, speed: Double): this(animatable.syncerType, animatable.syncData, time, speed)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: AnimSpeedChangePayload, context: IPayloadContext) {
            val level = context.player().level()
            val animatable = payload.syncerType.getSyncer(level, payload.syncData) as? IAnimatable<*> ?: return
            animatable.animController.changeSpeed(payload.time, payload.speed)
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<AnimSpeedChangePayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "anim_speed_change")
        )

        @JvmStatic
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, AnimSpeedChangePayload> = StreamCodec.composite(
            SyncerType.STREAM_CODEC, AnimSpeedChangePayload::syncerType,
            SyncData.STREAM_CODEC, AnimSpeedChangePayload::syncData,
            ByteBufCodecs.INT, AnimSpeedChangePayload::time,
            ByteBufCodecs.DOUBLE, AnimSpeedChangePayload::speed,
            ::AnimSpeedChangePayload
        )
    }
}