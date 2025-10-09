package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.animation.model.BonePose
import cn.solarmoon.spark_core.animation.anim.KeyAnimData
import cn.solarmoon.spark_core.animation.model.ModelInstance
import net.neoforged.bus.api.Event

open class BoneUpdateEvent(
    val model: ModelInstance,
    val bonePose: BonePose,
    val oldTransform: KeyAnimData,
    val originNewTransform: KeyAnimData
): Event() {

    var newTransform: KeyAnimData = originNewTransform

}