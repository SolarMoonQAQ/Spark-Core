package cn.solarmoon.spark_core.animation.anim.part

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.play.MixedAnimation
import cn.solarmoon.spark_core.phys.copy
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.min

/**
 * 骨骼动画，是一个完整动画的一部分，是动画的最小单位，受animation制约和控制
 */
data class BoneAnim(
    val rotation: LinkedHashMap<Double, KeyFrame>,
    val position: LinkedHashMap<Double, KeyFrame>,
    val scale: LinkedHashMap<Double, KeyFrame>
) {

    /**
     * 当前骨骼动画所从属的动画，会在动画加载时赋值，因此只要在世界内调用无需担心为null
     */
    var rootAnimation: Animation? = null

    private fun getPresentAnimValue(valueMap: LinkedHashMap<Double, KeyFrame>, mixedAnimation: MixedAnimation, partialTick: Float, defaultValue: Vector3f): Vector3f {
        val time = (mixedAnimation.tick + if (mixedAnimation.isInTransition || (mixedAnimation.isCancelled && mixedAnimation.transTick <= 0)) 0.0001f else partialTick * mixedAnimation.speed) / MixedAnimation.TICKS_PRE_SECOND
        // 在各个时间内两两遍历，定位到当前间隔进行变换
        valueMap.onEachIndexed { index, entry ->
            val kNow = entry
            val kTarget = valueMap.entries.elementAtOrElse(index + 1) { kNow }
            val tNow = kNow.key
            val tTarget = kTarget.key
            if (time >= tNow && time < tTarget) {
                val timeInternal = tTarget - tNow
                val progress = (time - tNow) / timeInternal
                return kTarget.value.interpolation.lerp(progress.toFloat(), valueMap, index)
            }
        }

        if (valueMap.isNotEmpty() && time >= valueMap.keys.last()) {
            return valueMap.values.last().post.toVector3f()
        }

        return defaultValue
    }

    /**
     * 获取当前绑定实体在当前动画的当前tick所对应的旋转值，也就是说该值没有额外加工，仅是代表了动画文件里某一tick的旋转
     * @return 进行过基础偏移后的旋转弧度角
     */
    fun getPresentAnimRot(mixedAnimation: MixedAnimation, partialTick: Float = 0F): Vector3f {
        return getPresentAnimValue(rotation, mixedAnimation, partialTick, Vector3f())
    }

    /**
     * 获取当前绑定实体在当前动画的当前tick所对应的位移值，也就是说该值没有额外加工，仅是代表了动画文件里某一tick的位移
     * @return 获取指定tick位置的位移数值，如果不在任何区间内，返回第一个位置
     */
    fun getPresentAnimPos(mixedAnimation: MixedAnimation, partialTick: Float = 0F): Vector3f {
        return getPresentAnimValue(position, mixedAnimation, partialTick, Vector3f())
    }

    /**
     * 获取当前绑定实体在当前动画的当前tick所对应的缩放值，也就是说该值没有额外加工，仅是代表了动画文件里某一tick的缩放
     * @return 获取指定tick位置的缩放数值，如果不在任何区间内，返回第一个位置
     */
    fun getPresentAnimScale(mixedAnimation: MixedAnimation, partialTick: Float = 0F): Vector3f {
        return getPresentAnimValue(scale, mixedAnimation, partialTick, Vector3f(1f))
    }

    companion object {
        @JvmStatic
        val CODEC: Codec<BoneAnim> = RecordCodecBuilder.create {
            it.group(
                KeyFrame.MAP_CODEC.optionalFieldOf("rotation", linkedMapOf()).forGetter { it.rotation },
                KeyFrame.MAP_CODEC.optionalFieldOf("position", linkedMapOf()).forGetter { it.position },
                KeyFrame.MAP_CODEC.optionalFieldOf("scale", linkedMapOf()).forGetter { it.scale }
            ).apply(it, ::BoneAnim)
        }

        @JvmStatic
        val MAP_CODEC = Codec.unboundedMap(Codec.STRING, CODEC).xmap( { LinkedHashMap(it) }, { it } )

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            KeyFrame.MAP_STREAM_CODEC, BoneAnim::rotation,
            KeyFrame.MAP_STREAM_CODEC, BoneAnim::position,
            KeyFrame.MAP_STREAM_CODEC, BoneAnim::scale,
            ::BoneAnim
        )

        @JvmStatic
        val MAP_STREAM_CODEC = ByteBufCodecs.map(
            ::LinkedHashMap,
            ByteBufCodecs.STRING_UTF8,
            STREAM_CODEC
        )
    }

}
