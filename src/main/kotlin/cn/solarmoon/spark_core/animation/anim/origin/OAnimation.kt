package cn.solarmoon.spark_core.animation.anim.origin

import cn.solarmoon.spark_core.js.molang.JSMolangValue
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

/**
 * 单个动画，其中包括了该动画所需的所有骨骼变换
 */
data class OAnimation(
    val loop: Loop,
    val animationLength: Float,
    val bones: LinkedHashMap<String, OBoneAnimation>,
    val timeline: LinkedHashMap<Float, List<JSMolangValue>>
) {

    init {
        bones.values.forEach { it.rootAnimation = this }
    }

    fun getBoneAnimation(name: String) = bones[name]

    companion object {
        @JvmStatic
        val TIMELINE_CODEC: Codec<LinkedHashMap<Float, List<JSMolangValue>>> =
            Codec.unboundedMap(
                Codec.STRING,
                Codec.either(JSMolangValue.CODEC, Codec.list(JSMolangValue.CODEC))
            ).xmap(
                { map ->
                    LinkedHashMap(map.mapKeys { it.key.toFloat() }.mapValues { (_, v) ->
                        v.map({ listOf(it) }, { it }) // Either.Left -> 单个值转 List, Either.Right -> 已经是 List
                    })
                },
                { linked ->
                    linked.mapKeys { it.key.toString() }.mapValues { (_, list) ->
                        if (list.size == 1) {
                            com.mojang.datafixers.util.Either.left(list[0])
                        } else {
                            com.mojang.datafixers.util.Either.right(list)
                        }
                    }
                }
            )

        @JvmStatic
        val CODEC: Codec<OAnimation> = RecordCodecBuilder.create {
            it.group(
                Loop.CODEC.optionalFieldOf("loop", Loop.ONCE).forGetter { it.loop },
                Codec.FLOAT.optionalFieldOf("animation_length", 9999999.0f).forGetter { it.animationLength },
                // bones 可选：YSM/Blockbench 导出的动画里存在只带 loop / animation_length 的
                // 纯占位或纯 timeline 条目（如 ——默认动画——、parallel4~6、pre_parallel2~7）。
                // 缺省回填空 map，语义等价于「不涉及任何骨骼的空动画」，权重恒 0、无副作用；
                // 若仍用 fieldOf 则会整份动画文件解析失败（AnimationModule 直接丢弃该文件）。
                OBoneAnimation.MAP_CODEC.optionalFieldOf("bones", LinkedHashMap()).forGetter { it.bones },
                TIMELINE_CODEC.optionalFieldOf("timeline", LinkedHashMap()).forGetter { it.timeline }
            ).apply(it, ::OAnimation)
        }

        @JvmStatic
        val MAP_CODEC = Codec.unboundedMap(Codec.STRING, CODEC).xmap({ LinkedHashMap(it) }, { it })

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            Loop.STREAM_CODEC, OAnimation::loop,
            ByteBufCodecs.FLOAT, OAnimation::animationLength,
            OBoneAnimation.MAP_STREAM_CODEC, OAnimation::bones,
            ByteBufCodecs.map(::LinkedHashMap, ByteBufCodecs.FLOAT, JSMolangValue.STREAM_CODEC.apply(ByteBufCodecs.list())), OAnimation::timeline,
            ::OAnimation
        )

        @JvmStatic
        val MAP_STREAM_CODEC = ByteBufCodecs.map(::LinkedHashMap, ByteBufCodecs.STRING_UTF8, STREAM_CODEC)
    }

}
