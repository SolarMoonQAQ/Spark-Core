package cn.solarmoon.spark_core.animation.anim.play.blend

import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import net.minecraft.util.Mth

data class BlendAnimation(
    val anim: AnimInstance,
    val weight: Double = 1.0,
    val enterTransitionTime: Int = 7,
    var exitTransitionTime: Int = 7,
    val blendMask: BlendMask = BlendMask()
) {

    init {
        anim.isCancelled = false
        if (weight <= 0) throw IllegalArgumentException("混合权重不可为0及以下的值")
    }
    
    private var targetWeight = weight
    private var startWeight = 0.0
    private var transitionTick = 0

    var transitionState = TransitionState.ENTER
        private set
    var currentWeight = 0.0
        private set

    val transitionProgress get() = when(transitionState) {
        TransitionState.ENTER -> Mth.clamp(transitionTick.toDouble() / enterTransitionTime, 0.0, 1.0)
        TransitionState.NONE -> 1.0
        TransitionState.EXIT -> Mth.clamp(transitionTick.toDouble() / exitTransitionTime, 0.0, 1.0)
    }

    val isInTransition get() = transitionState != TransitionState.NONE

    var shouldClearWhenResetAnim = false
    
    val isRemoved get() = currentWeight < 0.0001 && targetWeight == 0.0

    fun doTransition() {
        transitionTick++
        currentWeight = Mth.lerp(transitionProgress, startWeight, targetWeight)
        if (transitionProgress >= 1.0) transitionState = TransitionState.NONE
    }
    
    fun markedForRemoval() {
        transitionTick = 0
        targetWeight = 0.0
        startWeight = currentWeight
        transitionState = TransitionState.EXIT
    }

}