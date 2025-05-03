package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.event.BoneUpdateEvent
import cn.solarmoon.spark_core.physics.rotLerp
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.NeoForge

class Bone(
    val holder: IAnimatable<*>,
    val name: String
) {

    private var _data = KeyAnimData()
    private var _oData = KeyAnimData()

    var data = _data
    var oData = _oData

    /**
     * 内部更新骨骼数据，更新不会立刻生效，将在下一个主线程tick开始时统一更新
     */
    fun updateInternal(newData: KeyAnimData) {
        _oData = _data
        _data = newData
    }

    /**
     * 在主线程调用，每tick从动画线程更新最新的动画变换数据到骨骼
     */
    fun setChanged() {
        val event = NeoForge.EVENT_BUS.post(BoneUpdateEvent(holder, this, data, _data))
        oData = event.oldData
        data = event.newData
    }

    fun getPosition(partialTicks: Number = 1.0) = oData.position.lerp(data.position, partialTicks.toDouble())

    fun getRotation(partialTicks: Number = 1.0) = oData.rotation.rotLerp(data.rotation, partialTicks.toDouble())

    fun getScale(partialTicks: Number = 1.0): Vec3 = oData.scale.lerp(data.scale, partialTicks.toDouble())

    fun copy() = Bone(holder, name).apply {
        this@apply.data = this@Bone.data
        this@apply.oData = this@Bone.data
    }

}