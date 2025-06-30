package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.blend.BlendAnimation
import cn.solarmoon.spark_core.animation.anim.play.blend.BlendData
import cn.solarmoon.spark_core.animation.anim.play.blend.BlendSpace
import net.minecraft.world.entity.Entity

class AnimController(
    val animatable: IAnimatable<*>
) {

    var mainAnim: AnimInstance? = null
        private set
    var speedChangeTime: Int = 0
        private set
    var overallSpeed: Double = 1.0
        private set

    val blendSpace = BlendSpace()

    /**
     * 设置当前动画并处理动画切换逻辑
     * @param anim 要设置的新动画实例（可为空）
     * @param transTime 动画过渡时间（单位：tick）
     * 
     * 处理流程：
     * 1. 验证动画有效性，若骨骼缺失则记录警告并终止
     * 2. 处理主动画的拒绝策略和事件触发
     * 3. 更新动画状态并维护骨骼混合空间
     */
    fun setAnimation(anim: AnimInstance?, transTime: Int) {
        if (anim != null) {
            /** 检查动画所需的骨骼有效性 */
            val valid = testAnimValidity(anim)
            if (valid.isNotEmpty()) {
                anim.isCancelled = false
                anim.cancel()
                SparkCore.LOGGER.warn("缺少要播放的动画所需的骨骼：$valid,UUID为 ${(animatable.animatable as Entity).stringUUID}, 名称为 ${(animatable.animatable as Entity).name} 的entity的动画 ${anim.index} 无法播放")
                return
            }
        }
        /** 处理动画切换逻辑 */
        if (mainAnim?.rejectNewAnim?.invoke(anim) == true && mainAnim?.isCancelled != true) return
        mainAnim?.cancel()
        mainAnim?.triggerEvent(AnimEvent.SwitchOut(anim))
        mainAnim = anim
        anim?.isCancelled = false
        anim?.triggerEvent(AnimEvent.SwitchIn(mainAnim))
        /** 维护骨骼混合空间 */
        anim?.let {
            (blendSpace.blendAnimMap.filter { it.value.data.shouldClearWhenResetAnim } + blendSpace.mainAnimMap).forEach {
                // 涉及到同一时刻切换的动画，需要同步其进出的过渡时间，否则其中一者的权重会迅速增大或降低导致权重失衡，看上去动画如同闪现一般
                it.value.data.exitTransitionTime = transTime
                it.value.markedForRemoval()
            }
            val blendAnim = BlendAnimation(anim, BlendData(1.0, transTime))
            blendSpace.putMainAnim(blendAnim)
        }
    }

    /**
     * 设置当前主动画，当动画无法找到时不进行任何操作
     * @param modifier 待输入的动画实例，可在此对其进行内部参数的修改
     */
    fun setAnimation(name: String, transTime: Int, modifier: AnimInstance.() -> Unit = {}) {
        val anim = AnimInstance.create(animatable, name, modifier)
        setAnimation(anim, transTime)
    }

    fun setAnimation(typed: TypedAnimation, transTime: Int) {
        setAnimation(typed.create(animatable), transTime)
    }

    fun blendAnimation(anim: BlendAnimation) {
        blendSpace.putBlendAnim(anim)
    }

    fun removeBlend(id: String) {
        blendSpace.removeBlend(id)
    }

    fun stopAnimation() {
        mainAnim?.cancel()
    }

    fun isPlaying(name: String): Boolean {
        val anim = mainAnim
        return anim != null && anim.index.name == name && !anim.isCancelled
    }

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
        blendSpace.physTick(overallSpeed)

        animatable.model.bones.forEach { entry ->
            val bone = animatable.getBone(entry.key)
            bone.updateInternal(blendSpace.blendBone(entry.key,animatable))
        }

        if (speedChangeTime > 0) speedChangeTime--
        else overallSpeed = 1.0
    }

    fun tick() {
        animatable.model.bones.forEach {
            animatable.getBone(it.key).setChanged()
        }

        blendSpace.tick()
    }
}