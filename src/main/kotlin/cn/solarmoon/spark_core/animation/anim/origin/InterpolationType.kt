package cn.solarmoon.spark_core.animation.anim.origin

import com.mojang.serialization.Codec
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.util.Mth
import org.joml.Vector3d
import org.joml.Vector3f
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVector3f
import kotlin.math.max
import kotlin.math.min

enum class InterpolationType {
    LINEAR, CATMULLROM;

    fun lerp(progress: Float, keyFrames: LinkedHashMap<Double, OKeyFrame>, presentIndex: Int): Vector3f {
        val progress = min(progress.toFloat(), 1f)
        val keyFrameGroup = keyFrames.values
        val kPre = keyFrameGroup.elementAt(max(presentIndex - 1, 0)).pre
        val kNow = keyFrameGroup.elementAt(presentIndex).post
        val kTarget = keyFrameGroup.elementAt(min(presentIndex + 1, keyFrameGroup.size - 1)).pre
        val kPost = keyFrameGroup.elementAt(min(presentIndex + 2, keyFrameGroup.size - 1)).post

        // 在终点就不lerp了
        if (presentIndex == keyFrameGroup.size - 1) return kNow.toVector3f()

        when(this) {
            LINEAR -> {
                val x = Mth.lerp(progress.toDouble(), kNow.x, kTarget.x)
                val y = Mth.lerp(progress.toDouble(), kNow.y, kTarget.y)
                val z = Mth.lerp(progress.toDouble(), kNow.z, kTarget.z)
                return Vector3d(x, y, z).toVector3f()
            }
            CATMULLROM -> {
                val x = Mth.catmullrom(progress, kPre.x.toFloat(), kNow.x.toFloat(), kTarget.x.toFloat(), kPost.x.toFloat())
                val y = Mth.catmullrom(progress, kPre.y.toFloat(), kNow.y.toFloat(), kTarget.y.toFloat() , kPost.y.toFloat())
                val z = Mth.catmullrom(progress, kPre.z.toFloat(), kNow.z.toFloat(), kTarget.z.toFloat(), kPost.z.toFloat())
                return Vector3f(x, y, z)
            }
        }
    }

    companion object {
        @JvmStatic
        val CODEC: Codec<InterpolationType> = Codec.STRING.xmap(
            { name -> valueOf(name.uppercase()) },
            { type -> type.name.lowercase() }
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