package cn.solarmoon.spark_core.animation.anim.play.layer

import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import net.minecraft.util.Mth

class AnimSpace(
    val anim: AnimInstance
) {

    var transTick = 0
    var transTime = 7
    var currentWeight = 0.0
    var startWeight = 0.0
    var targetWeight = 0.0
    var transitionState = TransitionState.ENTER

    val transProgress get() = when(transitionState) {
        TransitionState.NONE -> 1.0
        else -> if (transTime != 0) (transTick.toDouble() / transTime) else 1.0
    }

    val isInTransition get() = transitionState != TransitionState.NONE

    val isRemoved get() = currentWeight == 0.0 && targetWeight == 0.0

    fun setWeight(weight: Double, transTime: Int) {
        startWeight = currentWeight
        targetWeight = weight
        transTick = 0
        this.transTime = transTime
        if (transTime == 0) transitionState = TransitionState.NONE
        if (weight == 0.0) transitionState = TransitionState.EXIT
    }

    fun start(transTime: Int) {
        setWeight(1.0, transTime)
    }

    fun end(transTime: Int) {
        setWeight(0.0, transTime)
    }

    fun physicsTick(overallSpeed: Double) {
        currentWeight = Mth.lerp(transProgress, startWeight, targetWeight)

        if (transTick < transTime) {
            transTick++
            if (transProgress >= 1.0) transitionState = TransitionState.NONE
        } else anim.physTick(overallSpeed)
    }

    fun tick() {
        anim.tick()
    }

}