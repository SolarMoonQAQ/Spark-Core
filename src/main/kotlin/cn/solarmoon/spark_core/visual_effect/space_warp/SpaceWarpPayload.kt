package cn.solarmoon.spark_core.visual_effect.space_warp

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.handling.IPayloadContext

data class SpaceWarpPayload(
    val pos: Vec3,
    val maxRadiusMeters: Float,
    val baseStrength: Float,
    val lifeTime: Int,
    val pulseHz: Float
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> = TYPE

    companion object {
        @JvmStatic
        fun handleInClient(payload: SpaceWarpPayload, context: IPayloadContext) {
            val warp = SpaceWarp(
                pos = payload.pos,
                maxRadiusMeters = payload.maxRadiusMeters,
                baseStrength = payload.baseStrength,
                maxLifeTime = payload.lifeTime,
                pulseHz = payload.pulseHz
            )
            SparkVisualEffects.SPACE_WARP.add(warp)
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SpaceWarpPayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "space_warp_spawn")
        )

        @JvmStatic
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, SpaceWarpPayload> =
            object : StreamCodec<FriendlyByteBuf, SpaceWarpPayload> {
                override fun encode(buf: FriendlyByteBuf, value: SpaceWarpPayload) {
                    SerializeHelper.VEC3_STREAM_CODEC.encode(buf, value.pos)
                    buf.writeFloat(value.maxRadiusMeters)
                    buf.writeFloat(value.baseStrength)
                    buf.writeInt(value.lifeTime)
                    buf.writeFloat(value.pulseHz)
                }
                override fun decode(buf: FriendlyByteBuf): SpaceWarpPayload {
                    val pos = SerializeHelper.VEC3_STREAM_CODEC.decode(buf)
                    val radius = buf.readFloat()
                    val strength = buf.readFloat()
                    val lifeTime = buf.readInt()
                    val pulse = buf.readFloat()
                    return SpaceWarpPayload(pos, radius, strength, lifeTime, pulse)
                }
            }
    }
}
