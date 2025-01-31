package cn.solarmoon.spark_core.visual_effect.camera_shake

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

data class CameraShakePayload(
    val time: Int,
    val strength: Float,
    val frequency: Float,
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: CameraShakePayload, context: IPayloadContext) {
            SparkVisualEffects.CAMERA_SHAKE.shake(payload.time, payload.strength, payload.frequency)
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<CameraShakePayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "visual_effect_shake"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, CameraShakePayload::time,
            ByteBufCodecs.FLOAT, CameraShakePayload::strength,
            ByteBufCodecs.FLOAT, CameraShakePayload::frequency,
            ::CameraShakePayload
        )
    }

}