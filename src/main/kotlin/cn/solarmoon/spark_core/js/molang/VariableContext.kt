package cn.solarmoon.spark_core.js.molang

import cn.solarmoon.spark_core.animation.anim.AnimInstance

class VariableContext(
    val anim: AnimInstance
) {

    val animatable get() = anim.holder
    val level get() = animatable.animLevel

}