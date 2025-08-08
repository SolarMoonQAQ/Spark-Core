package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.BonePose
import cn.solarmoon.spark_core.animation.anim.play.KeyAnimData
import net.neoforged.bus.api.Event

open class BoneUpdateEvent(
    val animatable: IAnimatable<*>,
    val bonePose: BonePose,
    val oldData: KeyAnimData,
    var newData: KeyAnimData
): Event() {

    class Vanilla(animatable: IAnimatable<*>, bonePose: BonePose, oldData: KeyAnimData, newData: KeyAnimData): BoneUpdateEvent(animatable, bonePose, oldData, newData)

}