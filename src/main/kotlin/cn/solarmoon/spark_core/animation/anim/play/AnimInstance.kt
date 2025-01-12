package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.anim.origin.OAnimation

class AnimInstance(
    val name: String,
    val animData: OAnimation
) {

    var time = 0.0
    var speed = 1.0
    var totalTime = 0.0
    var shouldTurnBody = false
    var isCancelled = false

    val step get() = speed / 20

    fun getProgress(partialTicks: Float = 0f) = ((time + partialTicks) / animData.animationLength).coerceIn(0.0, 1.0)

    fun step(speed: Double = 1.0) {
        time += step * speed
        totalTime += step * speed
    }

}