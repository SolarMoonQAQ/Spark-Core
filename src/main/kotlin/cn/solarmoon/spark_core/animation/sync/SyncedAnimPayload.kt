package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimModificationData
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

data class SyncedAnimPayload(
    val entityId: Int,
    val animId: Int,
    val modifier: AnimModificationData = AnimModificationData()
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: SyncedAnimPayload, context: IPayloadContext) {
            val level = context.player().level() as ClientLevel
            val entity = level.getEntity(payload.entityId)
            if (entity !is IAnimatable<*>) return
            SyncedAnimation.ALL_CONSUME_ANIMATIONS[payload.animId]?.consume(entity, payload.modifier)
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SyncedAnimPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "animation_consumeanim"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SyncedAnimPayload::entityId,
            ByteBufCodecs.INT, SyncedAnimPayload::animId,
            AnimModificationData.STREAM_CODEC, SyncedAnimPayload::modifier,
            ::SyncedAnimPayload
        )
    }

}