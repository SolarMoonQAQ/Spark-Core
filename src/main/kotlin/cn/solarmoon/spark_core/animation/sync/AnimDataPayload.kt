package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimData
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * 对整个animData的同步，每份大小约在127字节左右，不必担心tick级的调用（但还是尽量不要这么做）
 */
data class AnimDataPayload(
    val id: Int,
    val animData: AnimData,
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: AnimDataPayload, context: IPayloadContext) {
            val level = context.player().level() as ClientLevel
            val data = payload.animData
            val entity = level.getEntity(payload.id)
            if (entity !is IEntityAnimatable<*>) return
            entity.animData = data
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<AnimDataPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "animation_network_data"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, AnimDataPayload::id,
            AnimData.STREAM_CODEC, AnimDataPayload::animData,
            ::AnimDataPayload
        )
    }

}
