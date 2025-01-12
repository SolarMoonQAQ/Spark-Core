package cn.solarmoon.spark_core.animation.anim.origin

import cn.solarmoon.spark_core.data.SerializeHelper
import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVec3
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

data class OKeyFrame(
    var pre: Vec3,
    var post: Vec3,
    val interpolation: InterpolationType
) {

    companion object {
        @JvmStatic
        val CODEC: Codec<OKeyFrame> = Codec.either(
            Vec3.CODEC,
            RecordCodecBuilder.create<OKeyFrame> {
                it.group(
                    Vec3.CODEC.optionalFieldOf("pre").forGetter { Optional.ofNullable(it.pre) },
                    Vec3.CODEC.fieldOf("post").forGetter { it.post },
                    InterpolationType.CODEC.optionalFieldOf("lerp_mode", InterpolationType.LINEAR).forGetter { it.interpolation }
                ).apply(it) { preOp, post, type ->
                    val pre = preOp.getOrNull() ?: post
                    OKeyFrame(pre, post, type)
                }
            }
        ).xmap(
            { it.map({ OKeyFrame(it, it, InterpolationType.LINEAR) }, { it }) },
            { if (it.pre == it.post && it.interpolation == InterpolationType.LINEAR) Either.left(it.post) else Either.right(it) }
        )

        @JvmStatic
        val MAP_CODEC = Codec.either(
            Codec.unboundedMap(Codec.STRING, CODEC).xmap({ LinkedHashMap(it.mapKeys { it.key.toDouble() }) }, { it.mapKeys { it.key.toString() } }),
            Codec.either(
                Vec3.CODEC.flatComapMap({ linkedMapOf(Pair(0.0, OKeyFrame(it, it, InterpolationType.LINEAR))) }, { if (it.size == 1 && it.firstEntry().key == 0.0) DataResult.success(it.values.first().pre) else DataResult.error { "" } }),
                Codec.DOUBLE.flatComapMap({ linkedMapOf(Pair(0.0, OKeyFrame(Vector3d(it).toVec3(), Vector3d(it).toVec3(), InterpolationType.LINEAR)))}, { if (it.size == 1 && it.firstEntry().key == 0.0) DataResult.success(it.values.first().pre.x) else DataResult.error { "" } })
            ).xmap({ it.map({ it }, { it }) }, { Either.left(it) })
        ).xmap(
            { it.map( { it }, { it }) },
            { Either.right(it) }
        )

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SerializeHelper.VEC3_STREAM_CODEC, OKeyFrame::pre,
            SerializeHelper.VEC3_STREAM_CODEC, OKeyFrame::post,
            InterpolationType.STREAM_CODEC, OKeyFrame::interpolation,
            ::OKeyFrame
        )

        @JvmStatic
        val MAP_STREAM_CODEC = ByteBufCodecs.map(
            ::LinkedHashMap,
            ByteBufCodecs.DOUBLE,
            STREAM_CODEC
        )
    }

}
