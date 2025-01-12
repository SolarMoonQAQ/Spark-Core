package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.anim.origin.OAnimation

data class BlendAnimation(
    val name: String,
    val anim: OAnimation,
    val weight: Double = 1.0,
    val speed: Double = 1.0,
    val boneBlackList: List<String> = listOf()
) {

    val step get() = speed / 20

}