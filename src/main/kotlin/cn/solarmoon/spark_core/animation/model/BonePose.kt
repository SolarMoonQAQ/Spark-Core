package cn.solarmoon.spark_core.animation.model

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.KeyAnimData
import cn.solarmoon.spark_core.event.BoneUpdateEvent
import cn.solarmoon.spark_core.util.rotLerp
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.NeoForge
import org.joml.Matrix4f
import org.joml.Vector3f

class BonePose(
    val model: ModelInstance,
    val name: String
) {

    private var internalTransform = KeyAnimData()

    private var oLocalTransform = KeyAnimData()
    private var localTransform = KeyAnimData()

    /**
     * 内部线程更新骨骼数据，更新不会立刻生效，将在下一个主线程tick开始时统一更新
     */
    internal fun updateInternal(newData: KeyAnimData) {
        internalTransform = newData
    }

    /**
     * 在主线程调用，每tick从动画线程更新最新的动画变换数据到骨骼
     */
    internal fun setChanged() {
        val event = NeoForge.EVENT_BUS.post(BoneUpdateEvent(model, this, localTransform, internalTransform))
        oLocalTransform = event.oldTransform
        localTransform = event.newTransform
    }

    fun getLocalPosition(partialTicks: Number = 1.0) = oLocalTransform.position.lerp(localTransform.position, partialTicks.toDouble())

    fun getLocalRotation(partialTicks: Number = 1.0) = oLocalTransform.rotation.rotLerp(localTransform.rotation, partialTicks.toDouble())

    fun getLocalScale(partialTicks: Number = 1.0) = oLocalTransform.scale.lerp(localTransform.scale, partialTicks.toDouble())
    
    fun getLocalTransform(partialTicks: Number = 1.0) = oLocalTransform.lerp(localTransform, partialTicks.toDouble())
    
    fun getLocalTransformMatrix(partialTicks: Number = 1.0) = Matrix4f().apply {
        val bone = model.origin.getBone(name) ?: return@apply
        translate(bone.pivot.toVector3f())
        translate(getLocalPosition(partialTicks).toVector3f())
        rotateZYX(getLocalRotation(partialTicks).toVector3f())
        scale(getLocalScale(partialTicks).toVector3f())
        translate(bone.pivot.toVector3f().negate())
    }

    /**
     * 获取骨骼在模型空间的变换矩阵
     */
    fun getSpaceBoneMatrix(partialTick: Number = 1f): Matrix4f {
        val ma = Matrix4f()
        val bone = model.origin.getBone(name)
        bone?.applyTransformWithParents(model.bonePoses, ma, partialTick.toFloat())
        return ma
    }

    /**
     * 获取骨骼的枢轴点在模型空间的坐标
     * 注意：这里返回的是骨骼局部 pivot（加 offset）直接变换到模型空间的结果，
     * 不要在这里提前做骨骼矩阵变换，否则会在世界坐标计算时重复。
     */
    fun getSpaceBonePivot(offset: Vec3 = Vec3.ZERO): Vector3f {
        val bone = model.origin.getBone(name) ?: return Vector3f()
        return bone.pivot.add(offset).toVector3f()
    }

    /**
     * 获取骨骼在世界位置的变换矩阵
     * @param partialTick 主线程客户端的tick时间
     */
    fun getWorldBoneMatrix(partialTick: Number = 1f): Matrix4f {
        val ma = model.animatable.getWorldPositionMatrix(partialTick.toFloat())
        // 直接叠加骨骼的模型空间变换
        return ma.mul(getSpaceBoneMatrix(partialTick))
    }

    /**
     * 获取骨骼的枢轴点在世界位置上的坐标
     * @param partialTick 主线程客户端的tick时间
     */
    fun getWorldBonePivot(offset: Vec3 = Vec3.ZERO, partialTick: Number = 1f): Vector3f {
        val pivot = getSpaceBonePivot(offset) // 这里是骨骼局部 pivot
        val ma = getWorldBoneMatrix(partialTick) // 世界矩阵（已包含骨骼变换）
        return ma.transformPosition(pivot)
    }

    fun copy() = BonePose(model, name).apply {
        this@apply.internalTransform = this@BonePose.internalTransform
        this@apply.oLocalTransform = this@BonePose.oLocalTransform
        this@apply.localTransform = this@BonePose.localTransform
    }

}