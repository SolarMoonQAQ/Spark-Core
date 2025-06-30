package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.blend.BlendData
import cn.solarmoon.spark_core.animation.anim.play.blend.BlendMask
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

class TypedAnimBlendPayload(
    val id: Int,
    val animId: Int,
    val blendData: BlendData
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleBothSide(payload: TypedAnimBlendPayload, context: IPayloadContext) {
            val level = context.player().level()
            val entity = level.getEntity(payload.id)
            if (entity !is IEntityAnimatable<*>) return
            val anim = SparkRegistries.TYPED_ANIMATION.byId(payload.animId) ?: return
            anim.blend(entity, payload.blendData)
            // 从单人客户端发来的同步需要再同步给其它玩家客户端
            if (!level.isClientSide) anim.blendToClient(payload.id, payload.blendData, context.player() as? ServerPlayer)
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<TypedAnimBlendPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "typed_anim_blend"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, TypedAnimBlendPayload::id,
            ByteBufCodecs.INT, TypedAnimBlendPayload::animId,
            BlendData.STREAM_CODEC, TypedAnimBlendPayload::blendData,
            ::TypedAnimBlendPayload
        )
    }

}