package cn.solarmoon.spark_core.animation.anim.part

import cn.solarmoon.spark_core.animation.anim.play.MixedAnimation
import cn.solarmoon.spark_core.phys.copy
import cn.solarmoon.spark_core.phys.thread.PhysLevel
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import org.joml.Vector3f
import kotlin.math.min

/**
 * 骨骼动画，是一个完整动画的一部分，是动画的最小单位，受animation制约和控制
 */
data class BoneAnim(
    val name: String,
    val baseLifeTime: Double,
    val rotationSequence: ArrayList<KeyFrame>,
    val positionSequence: ArrayList<KeyFrame>,
    val scaleSequence: ArrayList<KeyFrame>
) {

    /**
     * 当前骨骼动画所从属的动画，会在动画加载时赋值，因此只要在世界内调用无需担心为null
     */
    var rootAnimation: Animation? = null

    /**
     * 获取当前绑定实体在当前动画的当前tick所对应的旋转值，也就是说该值没有额外加工，仅是代表了动画文件里某一tick的旋转
     * @return 进行过基础偏移后的旋转弧度角
     */
    fun getPresentAnimRot(mixedAnimation: MixedAnimation, partialTick: Float = 0F): Vector3f {
        val time = (mixedAnimation.tick + if (mixedAnimation.isInTransition || (mixedAnimation.isCancelled && mixedAnimation.transTick <= 0)) 0.0001f else partialTick * mixedAnimation.speed) / MixedAnimation.TICKS_PRE_SECOND
        // 在各个时间内两两遍历，定位到当前间隔进行变换
        rotationSequence.forEachIndexed { index, keyFrame ->
            val kNow = keyFrame.copy()
            val kTarget = rotationSequence[min(index + 1, rotationSequence.size - 1)].copy()
            val timestampA = kNow.timestamp
            val timestampB = kTarget.timestamp
            if (time >= timestampA && time < timestampB) {
                val timeInternal = timestampB - timestampA
                val progress = (time - timestampA) / timeInternal
                return kTarget.interpolation.lerp(progress.toFloat(), rotationSequence, index)
            }
        }

        if (rotationSequence.isNotEmpty() && time > rotationSequence.last().timestamp) {
            return rotationSequence.last().targetPost.copy()
        }

        return Vector3f()
    }

    /**
     * 获取当前绑定实体在当前动画的当前tick所对应的位移值，也就是说该值没有额外加工，仅是代表了动画文件里某一tick的位移
     * @return 获取指定tick位置的位移数值，如果不在任何区间内，返回第一个位置
     */
    fun getPresentAnimPos(mixedAnimation: MixedAnimation, partialTick: Float = 0F): Vector3f {
        val time = (mixedAnimation.tick + if (mixedAnimation.isInTransition || (mixedAnimation.isCancelled && mixedAnimation.transTick <= 0)) 0.0001f else partialTick * mixedAnimation.speed) / MixedAnimation.TICKS_PRE_SECOND

        // 在各个时间内两两遍历，定位到当前间隔进行变换
        positionSequence.forEachIndexed { index, keyFrame ->
            val kNow = keyFrame.copy()
            val kTarget = positionSequence[min(index + 1, positionSequence.size - 1)].copy()
            val timestampA = kNow.timestamp
            val timestampB = kTarget.timestamp
            if (time >= timestampA && time < timestampB) {
                val timeInternal = timestampB - timestampA
                val progress = (time - timestampA) / timeInternal
                return kTarget.interpolation.lerp(progress.toFloat(), positionSequence, index)
            }
        }

        if (positionSequence.isNotEmpty() && time >= positionSequence.last().timestamp) {
            return positionSequence.last().targetPost.copy()
        }

        return Vector3f()
    }

    /**
     * 获取当前绑定实体在当前动画的当前tick所对应的缩放值，也就是说该值没有额外加工，仅是代表了动画文件里某一tick的缩放
     * @return 获取指定tick位置的缩放数值，如果不在任何区间内，返回第一个位置
     */
    fun getPresentAnimScale(mixedAnimation: MixedAnimation, partialTick: Float = 0F): Vector3f {
        val time = (mixedAnimation.tick + if (mixedAnimation.isInTransition || (mixedAnimation.isCancelled && mixedAnimation.transTick <= 0)) 0.0001f else partialTick * mixedAnimation.speed) / MixedAnimation.TICKS_PRE_SECOND

        // 在各个时间内两两遍历，定位到当前间隔进行变换
        scaleSequence.forEachIndexed { index, keyFrame ->
            val kNow = keyFrame.copy()
            val kTarget = scaleSequence[min(index + 1, scaleSequence.size - 1)].copy()
            val timestampA = kNow.timestamp
            val timestampB = kTarget.timestamp
            if (time >= timestampA && time < timestampB) {
                val timeInternal = timestampB - timestampA
                val progress = (time - timestampA) / timeInternal
                return kTarget.interpolation.lerp(progress.toFloat(), scaleSequence, index)
            }
        }

        if (scaleSequence.isNotEmpty() && time > scaleSequence.last().timestamp) {
            return scaleSequence.last().targetPost.copy()
        }

        return Vector3f(1f)
    }

    fun copy(): BoneAnim {
        return BoneAnim(
            name,
            baseLifeTime,
            ArrayList(rotationSequence.map { it.copy() }.toList()),
            ArrayList(positionSequence.map { it.copy() }.toList()),
            ArrayList(scaleSequence.map { it.copy() }.toList())
        ).also { it.rootAnimation = this.rootAnimation }
    }

    companion object {
        @JvmStatic
        val CODEC: Codec<BoneAnim> = RecordCodecBuilder.create {
            it.group(
                Codec.STRING.fieldOf("name").forGetter { it.name },
                Codec.DOUBLE.fieldOf("base_lifetime").forGetter { it.baseLifeTime },
                KeyFrame.LIST_CODEC.fieldOf("rotation_sequence").forGetter { it.rotationSequence },
                KeyFrame.LIST_CODEC.fieldOf("position_sequence").forGetter { it.positionSequence },
                KeyFrame.LIST_CODEC.fieldOf("scale_sequence").forGetter { it.scaleSequence }
            ).apply(it) { a, b, c, d, e ->
                BoneAnim(a, b, ArrayList(c), ArrayList(d), ArrayList(e))
            }
        }

        @JvmStatic
        val LIST_CODEC = CODEC.listOf()

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BoneAnim::name,
            ByteBufCodecs.DOUBLE, BoneAnim::baseLifeTime,
            KeyFrame.LIST_STREAM_CODEC, BoneAnim::rotationSequence,
            KeyFrame.LIST_STREAM_CODEC, BoneAnim::positionSequence,
            KeyFrame.LIST_STREAM_CODEC, BoneAnim::scaleSequence,
            ::BoneAnim
        )

        @JvmStatic
        val LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.collection { arrayListOf() })
    }

}
