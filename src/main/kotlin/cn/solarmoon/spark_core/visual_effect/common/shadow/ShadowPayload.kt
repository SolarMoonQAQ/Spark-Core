package cn.solarmoon.spark_core.visual_effect.common.shadow

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.awt.Color

data class ShadowPayload(
    val entityId: Int,
    val maxLifeTime: Int,
    val color: Int
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: ShadowPayload, context: IPayloadContext) {
            val level = context.player().level() as ClientLevel
            val entity = level.getEntity(payload.entityId)
            if (entity !is IEntityAnimatable<*>) return
            SparkVisualEffects.SHADOW.add(Shadow(entity, payload.maxLifeTime, Color(payload.color)))
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<ShadowPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "sync_shadow"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ShadowPayload::entityId,
            ByteBufCodecs.INT, ShadowPayload::maxLifeTime,
            ByteBufCodecs.INT, ShadowPayload::color,
            ::ShadowPayload
        )
    }

}