package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.event.BoneUpdateEvent
import cn.solarmoon.spark_core.phys.rotLerp
import cn.solarmoon.spark_core.phys.toRadians
import net.neoforged.neoforge.common.NeoForge

class Bone(
    val holder: IAnimatable<*>,
    val name: String
) {

    var data = KeyAnimData()
        private set
    var oData = KeyAnimData()
        private set

    fun update(newData: KeyAnimData) {
        oData = data
        val event = NeoForge.EVENT_BUS.post(BoneUpdateEvent(holder, this, data, newData))
        data = event.newData
    }

    fun set(newData: KeyAnimData) {
        val event = NeoForge.EVENT_BUS.post(BoneUpdateEvent(holder, this, data, newData))
        oData = event.newData
        data = event.newData
    }

    fun getPosition(partialTicks: Number = 1.0) = oData.position.lerp(data.position, partialTicks.toDouble())

    fun getRotation(partialTicks: Number = 1.0) = oData.rotation.rotLerp(data.rotation, partialTicks.toDouble()).toRadians()

    fun getScale(partialTicks: Number = 1.0) = oData.scale.lerp(data.scale, partialTicks.toDouble())

    fun copy() = Bone(holder, name).apply {
        this@apply.data = this@Bone.data
        this@apply.oData = this@Bone.data
    }

}