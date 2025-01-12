package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.model.origin.OModel
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class SetAnimPayload(
    val id: Int,
    val animName: String,
    val transTime: Int,
    val speed: Double,
    val shouldTurnBody: Boolean
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: SetAnimPayload, context: IPayloadContext) {
            val level = context.player().level()
            val entity = level.getEntity(payload.id)
            if (entity !is IEntityAnimatable<*>) return
            entity.animController.setAnimation(payload.animName, payload.transTime) {
                it.speed = payload.speed
                it.shouldTurnBody = payload.shouldTurnBody
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SetAnimPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "set_anim"))

        @JvmStatic
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, SetAnimPayload> = StreamCodec.composite(
            ByteBufCodecs.INT, SetAnimPayload::id,
            ByteBufCodecs.STRING_UTF8, SetAnimPayload::animName,
            ByteBufCodecs.INT, SetAnimPayload::transTime,
            ByteBufCodecs.DOUBLE, SetAnimPayload::speed,
            ByteBufCodecs.BOOL, SetAnimPayload::shouldTurnBody,
            ::SetAnimPayload
        )
    }

}