package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

data class AnimFreezingPayload(
    val entityId: Int,
    val freezeSpeedPercent: Float,
    val maxFreezeTick: Int
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: AnimFreezingPayload, context: IPayloadContext) {
            val level = context.player().level() as ClientLevel
            val entity = level.getEntity(payload.entityId)
            if (entity !is IEntityAnimatable<*>) return
            entity.animController.startFreezing(false, payload.freezeSpeedPercent, payload.maxFreezeTick)
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<AnimFreezingPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "animation_freezing"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, AnimFreezingPayload::entityId,
            ByteBufCodecs.FLOAT, AnimFreezingPayload::freezeSpeedPercent,
            ByteBufCodecs.INT, AnimFreezingPayload::maxFreezeTick,
            ::AnimFreezingPayload
        )
    }

}