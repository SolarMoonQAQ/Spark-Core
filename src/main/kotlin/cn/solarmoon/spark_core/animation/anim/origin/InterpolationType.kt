package cn.solarmoon.spark_core.animation.anim.origin

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.AnimInstance
import cn.solarmoon.spark_core.js.eval
import cn.solarmoon.spark_core.js.getJSBindings
import cn.solarmoon.spark_core.js.molang.QueryContext
import cn.solarmoon.spark_core.js.put
import cn.solarmoon.spark_core.js.safeGetOrCreateJSContext
import cn.solarmoon.spark_core.molang.engine.runtime.ExpressionEvaluator
import cn.solarmoon.spark_core.util.toVector3f
import com.mojang.serialization.Codec
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.util.Mth
import org.graalvm.polyglot.Context
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.min

enum class InterpolationType {
    LINEAR, CATMULLROM;

    fun lerp(
        progress: Float,
        keyFrames: LinkedHashMap<Float, OKeyFrame>,
        presentIndex: Int,
        anim: AnimInstance,
    ): Vector3f {
        val progress = min(progress, 1f)
        val keyFrameGroup = keyFrames.values

        val kPre = keyFrameGroup.elementAt(max(presentIndex - 1, 0)).pre.eval(anim)
        val kNow = keyFrameGroup.elementAt(presentIndex).post.eval(anim)
        val kTarget = keyFrameGroup.elementAt(min(presentIndex + 1, keyFrameGroup.size - 1)).pre.eval(anim)
        val kPost = keyFrameGroup.elementAt(min(presentIndex + 2, keyFrameGroup.size - 1)).post.eval(anim)

        // 在终点就不lerp了
        if (presentIndex == keyFrameGroup.size - 1) return kNow

        when (this) {
            LINEAR -> {
                val x = Mth.lerp(progress.toDouble(), kNow.x.toDouble(), kTarget.x.toDouble())
                val y = Mth.lerp(progress.toDouble(), kNow.y.toDouble(), kTarget.y.toDouble())
                val z = Mth.lerp(progress.toDouble(), kNow.z.toDouble(), kTarget.z.toDouble())
                return Vector3d(x, y, z).toVector3f()
            }

            CATMULLROM -> {
                val x =
                    Mth.catmullrom(progress, kPre.x, kNow.x, kTarget.x, kPost.x)
                val y =
                    Mth.catmullrom(progress, kPre.y, kNow.y, kTarget.y, kPost.y)
                val z =
                    Mth.catmullrom(progress, kPre.z, kNow.z, kTarget.z, kPost.z)
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