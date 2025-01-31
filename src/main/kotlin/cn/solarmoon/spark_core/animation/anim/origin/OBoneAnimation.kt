package cn.solarmoon.spark_core.animation.anim.origin

import cn.solarmoon.spark_core.animation.anim.play.KeyAnimData
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import org.joml.Vector3f
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVec3

/**
 * 骨骼动画，是一个完整动画的一部分，是动画的最小单位，受animation制约和控制
 */
data class OBoneAnimation(
    val rotation: LinkedHashMap<Double, OKeyFrame>,
    val position: LinkedHashMap<Double, OKeyFrame>,
    val scale: LinkedHashMap<Double, OKeyFrame>
) {

    /**
     * 当前骨骼动画所从属的动画，会在动画加载时赋值，因此只要在世界内调用无需担心为null
     */
    var rootAnimation: OAnimation? = null

    private fun getPresentAnimValue(valueMap: LinkedHashMap<Double, OKeyFrame>, time: Double, defaultValue: Vector3f): Vector3f {
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
    fun getAnimRotAt(time: Double): Vector3f {
        return getPresentAnimValue(rotation, time, Vector3f())
    }

    /**
     * 获取当前绑定实体在当前动画的当前tick所对应的位移值，也就是说该值没有额外加工，仅是代表了动画文件里某一tick的位移
     * @return 获取指定tick位置的位移数值，如果不在任何区间内，返回第一个位置
     */
    fun getAnimPosAt(time: Double): Vector3f {
        return getPresentAnimValue(position, time, Vector3f())
    }

    /**
     * 获取当前绑定实体在当前动画的当前tick所对应的缩放值，也就是说该值没有额外加工，仅是代表了动画文件里某一tick的缩放
     * @return 获取指定tick位置的缩放数值，如果不在任何区间内，返回第一个位置
     */
    fun getAnimScaleAt(time: Double): Vector3f {
        return getPresentAnimValue(scale, time, Vector3f(1f))
    }

    fun getKeyAnimDataAt(time: Double): KeyAnimData {
        return KeyAnimData(
            getAnimPosAt(time).toVec3(),
            getAnimRotAt(time).toVec3(),
            getAnimScaleAt(time).toVec3()
        )
    }

    companion object {
        @JvmStatic
        val CODEC: Codec<OBoneAnimation> = RecordCodecBuilder.create {
            it.group(
                OKeyFrame.MAP_CODEC.optionalFieldOf("rotation", linkedMapOf()).forGetter { it.rotation },
                OKeyFrame.MAP_CODEC.optionalFieldOf("position", linkedMapOf()).forGetter { it.position },
                OKeyFrame.MAP_CODEC.optionalFieldOf("scale", linkedMapOf()).forGetter { it.scale }
            ).apply(it, ::OBoneAnimation)
        }

        @JvmStatic
        val MAP_CODEC = Codec.unboundedMap(Codec.STRING, CODEC).xmap( { LinkedHashMap(it) }, { it } )

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            OKeyFrame.MAP_STREAM_CODEC, OBoneAnimation::rotation,
            OKeyFrame.MAP_STREAM_CODEC, OBoneAnimation::position,
            OKeyFrame.MAP_STREAM_CODEC, OBoneAnimation::scale,
            ::OBoneAnimation
        )

        @JvmStatic
        val MAP_STREAM_CODEC = ByteBufCodecs.map(
            ::LinkedHashMap,
            ByteBufCodecs.STRING_UTF8,
            STREAM_CODEC
        )
    }

}
