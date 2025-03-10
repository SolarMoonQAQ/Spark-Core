package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable

class AnimController(
    val animatable: IAnimatable<*>
) {

    var mainAnim: AnimInstance? = null
        private set
    var lastAnim: AnimInstance? = null
        private set
    var transitionTick = 0
        private set
    var maxTransitionTick = 0
        private set
    var lastBlendResult = mapOf<String, KeyAnimData>()
        private set
    var speedChangeTime: Int = 0
        private set
    var overallSpeed: Double = 1.0
        private set
    val isInTransition get() = transitionTick > 0

    val blendSpace = BlendSpace()

    fun setAnimation(anim: AnimInstance?, transTime: Int) {
        if (anim != null) {
            val valid = testAnimValidity(anim)
            if (valid.isNotEmpty()) {
                SparkCore.LOGGER.warn("缺少要播放的动画所需的骨骼：$valid")
                return
            }
        }
        if (mainAnim?.rejectNewAnim?.invoke(anim) == true && mainAnim?.isCancelled != true) return
        mainAnim?.cancel()
        mainAnim?.triggerEvent(AnimEvent.SwitchOut(anim))
        lastAnim = mainAnim
        mainAnim = anim
        anim?.isCancelled = false
        anim?.triggerEvent(AnimEvent.SwitchIn(mainAnim))
        lastBlendResult = animatable.model.bones.mapValues { blendSpace.blendBone(it.key, animatable) }
        anim?.let {
            val removeList = blendSpace.filter { it.value.shouldClearWhenResetAnim }.map { it.key }
            removeList.forEach { blendSpace.remove(it) }
            blendSpace.put("main", BlendAnimation(anim, 1.0))
        }
        transitionTick = transTime
        maxTransitionTick = transitionTick
    }

    /**
     * 设置当前主动画，当动画无法找到时不进行任何操作
     * @param modifier 待输入的动画实例，可在此对其进行内部参数的修改
     */
    fun setAnimation(name: String, transTime: Int, modifier: AnimInstance.() -> Unit = {}) {
        animatable.animations.getAnimation(name)?.let {
            val anim = AnimInstance.create(animatable, name, it, modifier)
            setAnimation(anim, transTime)
        }
    }

    fun setAnimation(typed: TypedAnimation, transTime: Int) {
        setAnimation(typed.create(animatable), transTime)
    }

    fun stopAnimation() {
        mainAnim?.cancel()
    }

    fun isPlaying(name: String): Boolean {
        val anim = mainAnim
        return anim != null && anim.name == name && !anim.isCancelled
    }

    fun isNotPlaying() = mainAnim == null || mainAnim?.isCancelled == true

    /**
     * @return 当前模型缺少的播放[anim]所需的骨骼的集合，为空则不缺少，即该动画可播放
     */
    fun testAnimValidity(anim: AnimInstance): Set<String> {
        return anim.origin.bones.keys - animatable.model.bones.keys
    }

    fun getPlayingAnim(): AnimInstance? {
        val anim = mainAnim
        if (anim?.isCancelled == true) return null
        return mainAnim
    }

    fun getPlayingAnim(name: String): AnimInstance? {
        if (!isPlaying(name)) return null
        return mainAnim
    }

    /**
     * 在指定时间内改变动画整体速度，时间结束后复原
     */
    fun changeSpeed(time: Int, speed: Double) {
        overallSpeed = speed
        speedChangeTime = time
    }

    fun physTick() {
        if (transitionTick > 0) {
            val progress = (1 - transitionTick.toDouble() / maxTransitionTick.toDouble()).coerceIn(0.0, 1.0)
            animatable.model.bones.forEach {
                val targetData = blendSpace.blendBone(it.key,animatable)
                val result = (lastBlendResult[it.key] ?: KeyAnimData()).lerp(targetData, progress)
                animatable.getBone(it.key).update(result)
            }
            transitionTick--
        }

        else {
            blendSpace.physTick(overallSpeed)

            animatable.model.bones.forEach { entry ->
                val bone = animatable.getBone(entry.key)
                bone.update(blendSpace.blendBone(entry.key,animatable))
            }
        }

        if (speedChangeTime > 0) speedChangeTime--
        else overallSpeed = 1.0
    }

    fun tick() {
        if (!isInTransition) {
            val anim = mainAnim ?: return
            if (!anim.isCancelled) anim.tick()
        }
    }

}