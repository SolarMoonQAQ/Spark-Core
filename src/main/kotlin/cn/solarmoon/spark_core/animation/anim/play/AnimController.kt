package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.animation.sync.SetAnimPayload
import net.neoforged.neoforge.network.PacketDistributor

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
        lastAnim = mainAnim
        mainAnim = anim
        lastBlendResult = animatable.model.bones.mapValues { blendSpace.blendBone(it.key, lastAnim?.totalTime ?: 0.0) }
        anim?.let {
            blendSpace.clear()
            blendSpace.put(BlendAnimation(anim.name, anim.animData, 1.0, anim.speed))
        }
        transitionTick = transTime
        maxTransitionTick = transitionTick
    }

    /**
     * 设置当前主动画，当动画无法找到时不进行任何操作
     * @param modifier 待输入的动画实例，可在此对其进行内部参数的修改
     */
    fun setAnimation(name: String, transTime: Int, modifier: (AnimInstance) -> Unit = {}) {
        animatable.animations.getAnimation(name)?.let {
            val anim = AnimInstance(name, it)
            modifier.invoke(anim)
            setAnimation(anim, transTime)
        }
    }

    fun stopAnimation() {
        mainAnim?.isCancelled = true
    }

    fun isPlaying(name: String?): Boolean {
        val anim = mainAnim
        return if (name == null) anim == null
        else anim!= null && anim.name == name && !anim.isCancelled
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

    fun tick() {
        val anim = mainAnim

        if (transitionTick > 0) {
            val progress = (1 - transitionTick.toDouble() / maxTransitionTick.toDouble()).coerceIn(0.0, 1.0)
            animatable.model.bones.forEach {
                val targetData = anim?.animData?.getBoneAnimation(it.key)?.getKeyAnimDataAt(anim.time) ?: KeyAnimData()
                val result = (lastBlendResult[it.key] ?: KeyAnimData()).lerp(targetData, progress)
                animatable.getBone(it.key).update(result)
            }
            transitionTick--
        }

        else {
            anim ?: return

            when(anim.animData.loop) {
                Loop.TRUE -> {
                    anim.step()
                }
                Loop.ONCE -> {
                    if (anim.time < anim.animData.animationLength) anim.step(overallSpeed)
                    else {
                        anim.isCancelled = true
                    }
                }
                Loop.HOLD_ON_LAST_FRAME -> {
                    if (anim.time < anim.animData.animationLength) anim.step(overallSpeed)
                }
            }

            animatable.model.bones.forEach { entry ->
                val bone = animatable.getBone(entry.key)
                bone.update(blendSpace.blendBone(entry.key, anim.totalTime))
            }
        }

        if (speedChangeTime > 0) speedChangeTime--
        else overallSpeed = 1.0
    }

}