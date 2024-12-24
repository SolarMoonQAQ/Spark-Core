package cn.solarmoon.spark_core.animation.anim.part

import cn.solarmoon.spark_core.data.SerializeHelper.VECTOR3F_STREAM_CODEC
import cn.solarmoon.spark_core.phys.copy
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.util.ExtraCodecs
import org.joml.Vector3f

data class KeyFrame(
    val timestamp: Float,
    val targetPre: Vector3f,
    val targetPost: Vector3f,
    val interpolation: InterpolationType
) {

    fun copy() = KeyFrame(timestamp, targetPre.copy(), targetPost.copy(), interpolation)

    companion object {
        @JvmStatic
        val CODEC: Codec<KeyFrame> = RecordCodecBuilder.create {
            it.group(
                Codec.FLOAT.fieldOf("timestamp").forGetter { it.timestamp },
                ExtraCodecs.VECTOR3F.fieldOf("value").forGetter { it.targetPre },
                ExtraCodecs.VECTOR3F.fieldOf("value").forGetter { it.targetPost },
                InterpolationType.CODEC.fieldOf("transition_type").forGetter { it.interpolation }
            ).apply(it, ::KeyFrame)
        }

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, KeyFrame::timestamp,
            VECTOR3F_STREAM_CODEC, KeyFrame::targetPre,
            VECTOR3F_STREAM_CODEC, KeyFrame::targetPost,
            InterpolationType.STREAM_CODEC, KeyFrame::interpolation,
            ::KeyFrame
        )

        @JvmStatic
        val LIST_CODEC = CODEC.listOf()

        @JvmStatic
        val LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.collection { arrayListOf() })
    }

}
