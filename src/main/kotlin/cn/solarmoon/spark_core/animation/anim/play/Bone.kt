package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.event.BoneUpdateEvent
import cn.solarmoon.spark_core.phys.rotLerp
import cn.solarmoon.spark_core.phys.toRadians
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.NeoForge

class Bone(
    val holder: IAnimatable<*>,
    val name: String
) {

    var data = KeyAnimData()
        private set
    var oData = KeyAnimData()
        private set

    var vanillaData = KeyAnimData(scale = Vec3.ZERO)
        private set
    var oVanillaData = KeyAnimData(scale = Vec3.ZERO)
        private set

    fun update(newData: KeyAnimData) {
        val event = NeoForge.EVENT_BUS.post(BoneUpdateEvent(holder, this, data, newData))
        oData = event.oldData
        data = event.newData
    }

    fun updateVanilla(newData: KeyAnimData) {
        val event = NeoForge.EVENT_BUS.post(BoneUpdateEvent.Vanilla(holder, this, vanillaData, newData))
        oVanillaData = event.oldData
        vanillaData = event.newData
    }

    fun set(newData: KeyAnimData) {
        val event = NeoForge.EVENT_BUS.post(BoneUpdateEvent(holder, this, data, newData))
        oData = event.newData
        data = event.newData
    }

    fun getPosition(partialTicks: Number = 1.0, physPartialTicks: Number = 1.0) = oData.position.lerp(data.position, physPartialTicks.toDouble())
        .add(oVanillaData.position.lerp(vanillaData.position, partialTicks.toDouble()))

    fun getRotation(partialTicks: Number = 1.0, physPartialTicks: Number = 1.0) = oData.rotation.rotLerp(data.rotation, physPartialTicks.toDouble())
        .add(oVanillaData.rotation.rotLerp(vanillaData.rotation, partialTicks.toDouble()))

    fun getScale(partialTicks: Number = 1.0, physPartialTicks: Number = 1.0) = oData.scale.lerp(data.scale, physPartialTicks.toDouble())
        .add(oVanillaData.scale.lerp(vanillaData.scale, partialTicks.toDouble()))

    fun copy() = Bone(holder, name).apply {
        this@apply.data = this@Bone.data
        this@apply.oData = this@Bone.data
    }

}