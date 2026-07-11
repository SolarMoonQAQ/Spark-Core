package cn.solarmoon.spark_core.animation.model

import cn.solarmoon.spark_core.animation.anim.KeyAnimData
import cn.solarmoon.spark_core.util.rotLerp
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f

class BonePose(
    val model: ModelInstance,
    val name: String
) {

    private var internalTransform = KeyAnimData()

    internal var oLocalTransform = KeyAnimData()
    internal var localTransform = KeyAnimData()

    /**
     * 内部线程更新骨骼数据，更新不会立刻生效，将在下一个主线程tick开始时统一更新
     */
    internal fun updateInternal(newData: KeyAnimData) {
        internalTransform = newData
    }

    /**
     * 主线程调用，将物理线程写入的动画数据发布到本地变换（双缓冲，供渲染插值）。
     * <p>
     * 不再逐骨骼发送 BoneUpdateEvent（每帧 1500+ 次 EventBus.post 是性能灾难）。
     * IK 等需求应订阅模型级事件 {@link ModelPoseUpdatedEvent}，一次性获取所有骨骼。
     */
    internal fun setChanged() {
        oLocalTransform = localTransform
        localTransform = internalTransform
    }

    fun getLocalPosition(partialTicks: Number = 1.0) = oLocalTransform.position.lerp(localTransform.position, partialTicks.toDouble())

    fun getLocalRotation(partialTicks: Number = 1.0) = oLocalTransform.rotation.rotLerp(localTransform.rotation, partialTicks.toDouble())

    fun getLocalScale(partialTicks: Number = 1.0) = oLocalTransform.scale.lerp(localTransform.scale, partialTicks.toDouble())
    
    fun getLocalTransform(partialTicks: Number = 1.0) = oLocalTransform.lerp(localTransform, partialTicks.toDouble())
    
    fun getLocalTransformMatrix(partialTicks: Number = 1.0) = Matrix4f().apply {
        val bone = model.origin.getBone(name) ?: return@apply
        translate(bone.pivot.toVector3f())
        translate(getLocalPosition(partialTicks).toVector3f())
        rotateZYX(getLocalRotation(partialTicks).add(bone.rotation).toVector3f())
        scale(getLocalScale(partialTicks).toVector3f())
        translate(bone.pivot.toVector3f().negate())
    }

    fun getSpaceBoneMatrix(partialTick: Number = 1f): Matrix4f {
        val ma = Matrix4f()
        val bone = model.origin.getBone(name)
        bone?.applyTransformWithParents(model.pose, ma, partialTick.toFloat())
        return ma
    }

    fun getSpaceBonePivotMatrix(partialTicks: Number = 1f): Matrix4f {
        val ma = getSpaceBoneMatrix(partialTicks)
        val bone = model.origin.getBone(name) ?: return ma
        ma.translate(bone.pivot.toVector3f())
        return ma
    }

    fun getSpaceBonePivot(offset: Vec3 = Vec3.ZERO, partialTick: Number = 1f): Vector3f {
        val bone = model.origin.getBone(name) ?: return Vector3f()
        val spaceMatrix = getSpaceBoneMatrix(partialTick)
        return spaceMatrix.transformPosition(bone.pivot.add(offset).toVector3f())
    }

    /**
     * 获取骨骼在世界位置的变换矩阵
     */
    fun getWorldBoneMatrix(partialTick: Number = 1f): Matrix4f {
        val ma = model.animatable.getWorldPositionMatrix(partialTick.toFloat())
        return ma.mul(getSpaceBoneMatrix(partialTick))
    }

    fun getWorldBonePivotMatrix(partialTicks: Number = 1f): Matrix4f {
        return model.animatable.getWorldPositionMatrix(partialTicks.toFloat()).mul(getSpaceBonePivotMatrix(partialTicks))
    }

    fun getWorldBonePivot(offset: Vec3 = Vec3.ZERO, partialTicks: Number = 1f): Vector3f {
        return model.animatable.getWorldPositionMatrix(partialTicks.toFloat()).transformPosition(getSpaceBonePivot(offset, partialTicks))
    }

    fun copy() = BonePose(model, name).apply {
        this@apply.internalTransform = this@BonePose.internalTransform
        this@apply.oLocalTransform = this@BonePose.oLocalTransform
        this@apply.localTransform = this@BonePose.localTransform
    }

}