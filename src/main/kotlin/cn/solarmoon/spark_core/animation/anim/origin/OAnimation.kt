package cn.solarmoon.spark_core.animation.anim.origin

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

/**
 * 单个动画，其中包括了该动画所需的所有骨骼变换
 */
data class OAnimation(
    val loop: Loop,
    val animationLength: Double,
    val bones: LinkedHashMap<String, OBoneAnimation>
) {

    init {
        bones.values.forEach { it.rootAnimation = this }
    }

    fun getBoneAnimation(name: String) = bones[name]

    companion object {
        @JvmStatic
        val CODEC: Codec<OAnimation> = RecordCodecBuilder.create {
            it.group(
                Loop.CODEC.optionalFieldOf("loop", Loop.ONCE).forGetter { it.loop },
                Codec.DOUBLE.fieldOf("animation_length").forGetter { it.animationLength },
                OBoneAnimation.MAP_CODEC.fieldOf("bones").forGetter { it.bones }
            ).apply(it, ::OAnimation)
        }

        @JvmStatic
        val MAP_CODEC = Codec.unboundedMap(Codec.STRING, CODEC).xmap({ LinkedHashMap(it) }, { it })

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            Loop.STREAM_CODEC, OAnimation::loop,
            ByteBufCodecs.DOUBLE, OAnimation::animationLength,
            OBoneAnimation.MAP_STREAM_CODEC, OAnimation::bones,
            ::OAnimation
        )

        @JvmStatic
        val MAP_STREAM_CODEC = ByteBufCodecs.map(::LinkedHashMap, ByteBufCodecs.STRING_UTF8, STREAM_CODEC)
    }

}
