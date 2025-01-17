package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

class TypedAnimPayload(
    val id: Int,
    val animId: Int,
    val transTime: Int
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleBothSide(payload: TypedAnimPayload, context: IPayloadContext) {
            val level = context.player().level()
            val entity = level.getEntity(payload.id)
            if (entity !is IEntityAnimatable<*>) return
            val anim = SparkRegistries.TYPED_ANIMATION.byId(payload.animId) ?: return
            anim.play(entity, payload.transTime)
            if (!level.isClientSide) anim.syncToClient(payload.id, payload.transTime, context.player() as? ServerPlayer)
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<TypedAnimPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "typed_anim"))

        @JvmStatic
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, TypedAnimPayload> = StreamCodec.composite(
            ByteBufCodecs.INT, TypedAnimPayload::id,
            ByteBufCodecs.INT, TypedAnimPayload::animId,
            ByteBufCodecs.INT, TypedAnimPayload::transTime,
            ::TypedAnimPayload
        )
    }

}