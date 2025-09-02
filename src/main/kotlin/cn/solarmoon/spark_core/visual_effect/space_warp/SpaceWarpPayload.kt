package cn.solarmoon.spark_core.visual_effect.space_warp

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import cn.solarmoon.spark_core.util.SerializeHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.handling.IPayloadContext

data class SpaceWarpPayload(
    val pos: Vec3,
    val minRadius: Float,
    val maxRadius: Float,
    val baseStrength: Float,
    val lifeTime: Int
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> = TYPE

    companion object {
        @JvmStatic
        fun handleInClient(payload: SpaceWarpPayload, context: IPayloadContext) {
            val warp = SpaceWarp(
                pos = payload.pos,
                minRadius = payload.minRadius,
                maxRadius = payload.maxRadius,
                baseStrength = payload.baseStrength,
                maxLifeTime = payload.lifeTime,
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
                    buf.writeFloat(value.minRadius)
                    buf.writeFloat(value.maxRadius)
                    buf.writeFloat(value.baseStrength)
                    buf.writeInt(value.lifeTime)
                }
                override fun decode(buf: FriendlyByteBuf): SpaceWarpPayload {
                    val pos = SerializeHelper.VEC3_STREAM_CODEC.decode(buf)
                    val radius0 = buf.readFloat()
                    val radius1 = buf.readFloat()
                    val strength = buf.readFloat()
                    val lifeTime = buf.readInt()
                    return SpaceWarpPayload(pos, radius0, radius1, strength, lifeTime)
                }
            }
    }
}
