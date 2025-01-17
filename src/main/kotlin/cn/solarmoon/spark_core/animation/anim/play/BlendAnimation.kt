package cn.solarmoon.spark_core.animation.anim.play

data class BlendAnimation(
    val anim: AnimInstance,
    val weight: Double = 1.0,
    val boneBlackList: List<String> = listOf()
) {

    var shouldClearWhenResetAnim = true

}