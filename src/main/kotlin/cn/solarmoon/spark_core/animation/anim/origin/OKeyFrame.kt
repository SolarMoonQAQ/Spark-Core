package cn.solarmoon.spark_core.animation.anim.origin

import cn.solarmoon.spark_core.molang.core.value.DoubleValue
import cn.solarmoon.spark_core.molang.core.value.Vector3k
import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

data class OKeyFrame(
    var pre: Vector3k,
    var post: Vector3k,
    val interpolation: InterpolationType
) {

    companion object {
        @JvmStatic
        val CODEC: Codec<OKeyFrame> = Codec.either(
            Vector3k.CODEC,
            RecordCodecBuilder.create<OKeyFrame> {
                it.group(
                    Vector3k.CODEC.optionalFieldOf("pre").forGetter { Optional.ofNullable(it.pre) },
                    Vector3k.CODEC.fieldOf("post").forGetter { it.post },
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
                Vector3k.CODEC.flatComapMap({ linkedMapOf(Pair(0.0, OKeyFrame(it, it, InterpolationType.LINEAR))) }, { if (it.size == 1 && it.firstEntry().key == 0.0) DataResult.success(it.values.first().pre) else DataResult.error { "" } }),
                DoubleValue.DOUBLE_VALUE_CODEC.flatComapMap({ linkedMapOf(Pair(0.0, OKeyFrame(Vector3k(it,it,it), Vector3k(it,it,it), InterpolationType.LINEAR)))}, { if (it.size == 1 && it.firstEntry().key == 0.0) DataResult.success(it.values.first().pre.x) else DataResult.error { "" } })
            ).xmap({ it.map({ it }, { it }) }, { Either.left(it) })
        ).xmap(
            { it.map( { it }, { it }) },
            { Either.right(it) }
        )

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            Vector3k.STREAM_CODEC, OKeyFrame::pre,
            Vector3k.STREAM_CODEC, OKeyFrame::post,
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
