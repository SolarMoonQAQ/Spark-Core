package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.Bone
import cn.solarmoon.spark_core.animation.anim.play.KeyAnimData
import net.neoforged.bus.api.Event

open class BoneUpdateEvent(
    val animatable: IAnimatable<*>,
    val bone: Bone,
    val oldData: KeyAnimData,
    var newData: KeyAnimData
): Event() {

    class Vanilla(animatable: IAnimatable<*>, bone: Bone, oldData: KeyAnimData, newData: KeyAnimData): BoneUpdateEvent(animatable, bone, oldData, newData)

}