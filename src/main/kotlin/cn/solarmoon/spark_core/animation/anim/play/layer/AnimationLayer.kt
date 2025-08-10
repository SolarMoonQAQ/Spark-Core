package cn.solarmoon.spark_core.animation.anim.play.layer

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.animation.anim.play.AnimEvent
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.KeyAnimData
import cn.solarmoon.spark_core.util.toVec3
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth

class AnimationLayer(
    val id: ResourceLocation,
    val priority: Int, // 层级优先级，值越高优先级越高
) {

    var data = AnimLayerData()
    var animation: AnimInstance? = null
        private set

    val animationCache = mutableListOf<Pair<AnimationLayer, AnimInstance>>()

    private var targetWeight = 0.0
    private var transitionTick = 0
    private var poseCache = mapOf<String, KeyAnimData?>()

    var weight: Double
        get() = data.weight
        set(value) {
            if (value !in 0.0..1.0) throw IllegalArgumentException("动画层 $id 权重只能是 0.0 到 1.0 之间的值")
            data = data.copy(weight = value)
            targetWeight = value
        }

    var boneMask: BoneMask
        get() = data.boneMask
        set(value) {
            data = data.copy(boneMask = value)
        }

    var transitionTime: Int
        get() = data.transitionTime
        set(value) {
            data = data.copy(transitionTime = value)
        }

    var blendMode: BlendMode
        get() = data.blendMode
        set(value) {
            data = data.copy(blendMode = value)
        }

    val transitionProgress get() = Mth.clamp(if (transitionTime == 0) 1.0 else transitionTick.toDouble() / transitionTime, 0.0, 1.0)

    val isInTransition get() = transitionTick < transitionTime

    val isPlaying get() = animation != null || isInTransition

    fun setAnimation(anim: AnimInstance?, data: AnimLayerData = AnimLayerData()) {
        val currentAnim = animation
        val nextAnim = anim

        if (nextAnim != null) {
            /** 检查动画所需的骨骼有效性 */
            val valid = testAnimValidity(nextAnim)
            if (valid.isNotEmpty()) {
                nextAnim.isCancelled = false
                nextAnim.cancel()
                SparkCore.LOGGER.warn("缺少要播放的动画所需的骨骼：${nextAnim.holder.animatable} 的动画 ${nextAnim.animIndex} 无法播放")
                return
            }
        }

        if (currentAnim?.rejectNewAnim?.invoke(nextAnim) == true && currentAnim.isCancelled != true) return

        val event = currentAnim?.triggerEvent(AnimEvent.SwitchOut(nextAnim))
        val nextAnimE = event?.nextAnim ?: nextAnim
        currentAnim?.cancel()

        if (currentAnim == null) {
            poseCache = mapOf()
        } else {
            poseCache = currentAnim.origin.bones.mapValues { getBonePose(it.key, currentAnim.holder) }
        }

        this.data = data
        transitionTick = 0
        animation = nextAnimE
        animation?.isCancelled = false
        animation?.triggerEvent(AnimEvent.SwitchIn(currentAnim))
    }

    fun stopAnimation(transitionTime: Int = 7) {
        setAnimation(null, data.copy(transitionTime = transitionTime))
    }

    fun getBoneWeight(boneName: String): Double {
        return boneMask.getWeight(boneName) * weight
    }

    fun getBonePose(boneName: String, animatable: IAnimatable<*>): KeyAnimData? {
        val anim = animation ?: return if (isInTransition) poseCache[boneName]?.lerp(KeyAnimData(), transitionProgress) else null
        val oAnim = anim.origin
        val boneAnim = oAnim.getBoneAnimation(boneName) ?: return null
        val time = when (oAnim.loop) {
            Loop.TRUE -> anim.time % anim.maxLength
            Loop.ONCE -> anim.time
            Loop.HOLD_ON_LAST_FRAME -> anim.time
        }
        val currentPose = poseCache[boneName] ?: KeyAnimData()
        val targetPose = KeyAnimData(
            boneAnim.getAnimPosAt(time, animatable).toVec3(),
            boneAnim.getAnimRotAt(time, animatable).toVec3(),
            boneAnim.getAnimScaleAt(time, animatable).toVec3()
        )
        return currentPose.lerp(targetPose, transitionProgress)
    }

    fun physicsTick(overallSpeed: Double) {
        if (transitionTick < transitionTime) transitionTick++
        else animation?.physTick(overallSpeed)

        if (animation?.isCancelled == true) stopAnimation()
    }

    fun tick() {
        if (!isInTransition) animation?.tick()
    }

    /**
     * @return 当前模型缺少的播放[anim]所需的骨骼的集合，为空则不缺少，即该动画可播放
     */
    fun testAnimValidity(anim: AnimInstance): Set<String> {
        return anim.origin.bones.keys - anim.holder.model.bones.keys
    }

}