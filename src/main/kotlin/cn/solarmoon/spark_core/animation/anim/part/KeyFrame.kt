package cn.solarmoon.spark_core.animation.anim.part

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

data class KeyFrame(
    var pre: Vec3,
    var post: Vec3,
    val interpolation: InterpolationType
) {

    companion object {
        @JvmStatic
        val CODEC: Codec<KeyFrame> = Codec.either(
            Vec3.CODEC,
            RecordCodecBuilder.create<KeyFrame> {
                it.group(
                    Vec3.CODEC.optionalFieldOf("pre").forGetter { Optional.ofNullable(it.pre) },
                    Vec3.CODEC.fieldOf("post").forGetter { it.post },
                    InterpolationType.CODEC.optionalFieldOf("lerp_mode", InterpolationType.LINEAR).forGetter { it.interpolation }
                ).apply(it) { preOp, post, type ->
                    val pre = preOp.getOrNull() ?: post
                    KeyFrame(pre, post, type)
                }
            }
        ).xmap(
            { it.map({ KeyFrame(it, it, InterpolationType.LINEAR) }, { it }) },
            { if (it.pre == it.post && it.interpolation == InterpolationType.LINEAR) Either.left(it.post) else Either.right(it) }
        )

        @JvmStatic
        val MAP_CODEC = Codec.either(
            Codec.unboundedMap(Codec.STRING, CODEC).xmap({ LinkedHashMap(it.mapKeys { it.key.toDouble() }) }, { it.mapKeys { it.key.toString() } }),
            Codec.either(
                Vec3.CODEC.flatComapMap({ linkedMapOf(Pair(0.0, KeyFrame(it, it, InterpolationType.LINEAR))) }, { if (it.size == 1 && it.firstEntry().key == 0.0) DataResult.success(it.values.first().pre) else DataResult.error { "" } }),
                Codec.DOUBLE.flatComapMap({ linkedMapOf(Pair(0.0, KeyFrame(Vector3d(it).toVec3(), Vector3d(it).toVec3(), InterpolationType.LINEAR)))}, { if (it.size == 1 && it.firstEntry().key == 0.0) DataResult.success(it.values.first().pre.x) else DataResult.error { "" } })
            ).xmap({ it.map({ it }, { it }) }, { Either.left(it) })
        ).xmap(
            { it.map( { it }, { it }) },
            { Either.right(it) }
        )

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SerializeHelper.VEC3_STREAM_CODEC, KeyFrame::pre,
            SerializeHelper.VEC3_STREAM_CODEC, KeyFrame::post,
            InterpolationType.STREAM_CODEC, KeyFrame::interpolation,
            ::KeyFrame
        )

        @JvmStatic
        val MAP_STREAM_CODEC = ByteBufCodecs.map(
            ::LinkedHashMap,
            ByteBufCodecs.DOUBLE,
            STREAM_CODEC
        )
    }

}
