package cn.solarmoon.spark_core.ik.caliko

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.util.toVec3
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * 将 Caliko IK 解算结果应用回 Spark Core 的 Bone 对象。
 */
object IKApplier {
    // 骨骼在模型绑定姿态下指向的默认局部轴 (通常是 +Y 或 +X)
    // 需要根据你的模型约定来确定！这里假设是 +Y
    private val BONE_REFERENCE_AXIS = Vector3f(0f, 1f, 0f)

    /**
     * 将已解算的 IK 链状态应用到 AnimInstance 的骨骼上。
     * 所有计算都在本地空间中进行，以确保与动画系统的正确集成。
     *
     * @param animatable 持有 IK 链和目标 BoneGroup 的动画体实例。
     * @param chainName 已解算的 IK 链的名称。
     */
    fun applyIKResult(animatable: IAnimatable<*>, chainName: String) { // Changed parameter type
        val chain = animatable.ikChains[chainName] ?: run { // Get chain from IAnimatable
            SparkCore.LOGGER.warn("IKApplier: Chain '$chainName' not found for ${animatable.animatable}")
            return
        }
        val model = animatable.model
        val bones = animatable.bones // Get BoneGroup from IAnimatable

        // 注意：由于我们现在在本地空间中工作，不需要考虑实体的世界旋转
        // 所有骨骼的旋转都是相对于其父骨骼的本地旋转
        var parentLocalQuat = Quaternionf().identity() // 开始时使用单位四元数

        val calikoBones = chain.chain // 使用 getChain() 获取骨骼列表
        for (i in calikoBones.indices) {
            val fbBone = calikoBones[i]
            val boneName = fbBone.name ?: continue // Caliko 0.9.3 FabrikBone3D 有 getName()
            val sBone = bones[boneName] ?: continue // Spark Core Bone
            val oBone = model.getBone(boneName) ?: continue // Spark Core OBone (原始定义)

            // 1. 计算骨骼新的本地方向
            val startLoc = fbBone.startLocation // Caliko 0.9.3 有 getStartLocation()
            val endLoc = fbBone.endLocation     // Caliko 0.9.3 有 getEndLocation()
            val newLocalDir = Vector3f(endLoc.x - startLoc.x, endLoc.y - startLoc.y, endLoc.z - startLoc.z).normalize()

            // 2. 计算骨骼新的本地旋转四元数 (从参考轴旋转到新方向)
            val newLocalQuat = Quaternionf().rotationTo(BONE_REFERENCE_AXIS, newLocalDir)

            // 3. 计算骨骼相对于父骨骼的局部旋转
            // 如果有父骨骼，需要考虑父骨骼的旋转
            // newRelativeLocal = inv(parentLocal) * newLocal
            val newRelativeLocalQuat = parentLocalQuat.invert(Quaternionf()).mul(newLocalQuat)

            // 4. 获取骨骼初始的局部旋转 (来自 OBone.rotation, 假设是 ZYX 欧拉角 - 度)
            val initialEulerDeg = oBone.rotation.toVector3f()
            // 假设 OBone.rotation 是 ZYX 顺序
            val initialLocalQuat = Quaternionf().rotateXYZ(initialEulerDeg.x, initialEulerDeg.y, initialEulerDeg.z)

            // 5. 计算从初始局部旋转到新局部旋转的差值旋转 (Delta Rotation)
            // delta = inv(initialLocal) * newRelativeLocal
            val deltaQuat = initialLocalQuat.invert(Quaternionf()).mul(newRelativeLocalQuat)

            // 6. 将差值旋转转换为欧拉角 (弧度)
            val keyDataRotation = deltaQuat.getEulerAnglesZYX(Vector3f())
            // 7. 更新 Spark Core Bone 的 KeyAnimData
            val currentKeyData = sBone.data
            // 只更新旋转，保持位置和缩放不变 (IK 主要影响旋转)
            val newKeyData = currentKeyData.copy(rotation = keyDataRotation.toVec3())
            sBone.updateInternal(newKeyData) // 使用内部更新方法

            // 8. 更新父骨骼的本地旋转，为下一次迭代做准备
            parentLocalQuat = newLocalQuat // 当前骨骼的本地旋转成为下一个子骨骼的父旋转
        }
    }
}
