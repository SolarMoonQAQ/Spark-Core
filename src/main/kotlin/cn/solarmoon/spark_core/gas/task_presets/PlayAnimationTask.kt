package cn.solarmoon.spark_core.gas.task_presets

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.gas.Ability
import cn.solarmoon.spark_core.gas.AbilityTask

class PlayAnimationTask(
    override val ability: Ability,
    val animIndex: AnimIndex,
): AbilityTask {

    override fun start() {
        (ability.spec.asc.owner as? IAnimatable<*>)?.let { animatable ->

        }
    }

}