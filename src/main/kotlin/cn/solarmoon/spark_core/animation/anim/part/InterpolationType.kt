package cn.solarmoon.spark_core.animation.anim.part

import cn.solarmoon.spark_core.phys.copy
import com.mojang.serialization.Codec
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.util.Mth
import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.min

enum class InterpolationType {
    LINEAR, CATMULLROM;

    fun lerp(progress: Float, keyFrameGroup: ArrayList<KeyFrame>, presentIndex: Int): Vector3f {
        val progress = min(progress.toFloat(), 1f)
        val kPre = keyFrameGroup[max(presentIndex - 1, 0)].targetPost.copy()
        val kNow = keyFrameGroup[presentIndex].targetPost.copy()
        val kTarget = keyFrameGroup[min(presentIndex + 1, keyFrameGroup.size - 1)].targetPre.copy()
        val kPost = keyFrameGroup[min(presentIndex + 2, keyFrameGroup.size - 1)].targetPre.copy()
        when(this) {
            LINEAR -> {
                val x = Mth.lerp(progress.toDouble(), kNow.x.toDouble(), kTarget.x.toDouble()).toFloat()
                val y = Mth.lerp(progress.toDouble(), kNow.y.toDouble(), kTarget.y.toDouble()).toFloat()
                val z = Mth.lerp(progress.toDouble(), kNow.z.toDouble(), kTarget.z.toDouble()).toFloat()
                return Vector3f(x, y, z)
            }
            CATMULLROM -> {
                val x = Mth.catmullrom(progress, kPre.x, kNow.x, kTarget.x, kPost.x).toFloat()
                val y = Mth.catmullrom(progress, kPre.y, kNow.y, kTarget.y , kPost.y).toFloat()
                val z = Mth.catmullrom(progress, kPre.z, kNow.z, kTarget.z, kPost.z).toFloat()
                return Vector3f(x, y, z)
            }
        }
    }

    companion object {
        @JvmStatic
        val CODEC: Codec<InterpolationType> = Codec.STRING.xmap(
            { name -> valueOf(name) },
            { type -> type.name }
        )

        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, InterpolationType> {
            override fun decode(buffer: FriendlyByteBuf): InterpolationType {
                return buffer.readEnum(InterpolationType::class.java)
            }

            override fun encode(buffer: FriendlyByteBuf, value: InterpolationType) {
                buffer.writeEnum(value)
            }
        }
    }

}