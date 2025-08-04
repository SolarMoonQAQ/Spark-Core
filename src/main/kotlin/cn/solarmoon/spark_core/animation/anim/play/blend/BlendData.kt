package cn.solarmoon.spark_core.animation.anim.play.blend

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

data class BlendData(
    val weight: Double = 1.0,
    val enterTransitionTime: Int = 7,
    var exitTransitionTime: Int = 7,
    val blendMask: BlendMask = BlendMask(),
    val shouldClearWhenResetAnim: Boolean = false,
    val refreshIfExist: Boolean = true
) {

    companion object {
        val STREAM_CODEC = object: StreamCodec<FriendlyByteBuf, BlendData> {
            override fun decode(buffer: FriendlyByteBuf): BlendData {
                return BlendData(
                    buffer.readDouble(),
                    buffer.readInt(),
                    buffer.readInt(),
                    BlendMask.STREAM_CODEC.decode(buffer),
                    buffer.readBoolean(),
                    buffer.readBoolean()
                )
            }

            override fun encode(
                buffer: FriendlyByteBuf,
                value: BlendData
            ) {
                buffer.writeDouble(value.weight)
                buffer.writeInt(value.enterTransitionTime)
                buffer.writeInt(value.exitTransitionTime)
                BlendMask.STREAM_CODEC.encode(buffer, value.blendMask)
                buffer.writeBoolean(value.shouldClearWhenResetAnim)
                buffer.writeBoolean(value.refreshIfExist)
            }

        }
    }

}
