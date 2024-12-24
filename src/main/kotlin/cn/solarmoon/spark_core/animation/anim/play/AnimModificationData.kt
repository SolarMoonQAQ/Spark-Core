package cn.solarmoon.spark_core.animation.anim.play

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

data class AnimModificationData(
    val speed: Float = -1f,
    val startTransSpeed: Float = -1f,
    val tick: Float = -1f
) {

    companion object {
        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, AnimModificationData> {
            override fun decode(buffer: FriendlyByteBuf): AnimModificationData {
                val speed = buffer.readFloat()
                val strans = buffer.readFloat()
                val tick = buffer.readFloat()
                return AnimModificationData(speed, strans, tick)
            }

            override fun encode(buffer: FriendlyByteBuf, value: AnimModificationData) {
                buffer.writeFloat(value.speed)
                buffer.writeFloat(value.startTransSpeed)
                buffer.writeFloat(value.tick)
            }
        }
    }

}