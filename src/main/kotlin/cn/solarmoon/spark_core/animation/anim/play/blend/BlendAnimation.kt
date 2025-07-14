package cn.solarmoon.spark_core.animation.anim.play.blend

import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import net.minecraft.util.Mth

class BlendAnimation(
    val anim: AnimInstance,
    val data: BlendData
) {

    init {
        anim.isCancelled = false
        if (data.weight <= 0) throw IllegalArgumentException("混合权重不可为0及以下的值")
    }

    val id get() = anim.animIndex.locationName
    
    private var targetWeight = data.weight
    private var startWeight = 0.0
    private var transitionTick = 0

    var transitionState = TransitionState.ENTER
        private set
    var currentWeight = 0.0
        private set

    val transitionProgress get() = when(transitionState) {
        TransitionState.ENTER -> Mth.clamp(transitionTick.toDouble() / data.enterTransitionTime, 0.0, 1.0)
        TransitionState.NONE -> 1.0
        TransitionState.EXIT -> Mth.clamp(transitionTick.toDouble() / data.exitTransitionTime, 0.0, 1.0)
    }

    val isInTransition get() = transitionState != TransitionState.NONE
    
    val isRemoved get() = currentWeight < 0.0001 && targetWeight == 0.0

    fun doTransition() {
        transitionTick++
        currentWeight = Mth.lerp(transitionProgress, startWeight, targetWeight)
        if (transitionProgress >= 1.0) transitionState = TransitionState.NONE
    }
    
    fun markedForRemoval() {
        if (transitionState != TransitionState.EXIT) {
            transitionTick = 0
            targetWeight = 0.0
            startWeight = currentWeight
            transitionState = TransitionState.EXIT
        }
    }

}