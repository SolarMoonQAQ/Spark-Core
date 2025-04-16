package cn.solarmoon.spark_core.ik.caliko

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import jme3utilities.math.MyMath.lerp
import jme3utilities.math.MyMath.toRadians
import net.minecraft.world.entity.Entity // Import Entity
import net.minecraft.world.phys.Vec3
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

        // --- 获取实体完整的初始世界旋转 ---
        var parentWorldQuat = Quaternionf().identity() // Start with identity
        val hostEntity = animatable.animatable as? Entity // Try to get the underlying entity
        if (hostEntity != null) {
            val partialTicks = animatable.partialTicks // Get partial ticks once
            // 获取插值后的 Yaw (Y 轴旋转) 和 Pitch (X 轴旋转) - 转换为弧度
            val yawRad = toRadians(lerp(partialTicks, hostEntity.yRotO, hostEntity.yRot)).toFloat()
            val pitchRad = toRadians(lerp(partialTicks, hostEntity.xRotO, hostEntity.xRot)).toFloat()
            // 应用旋转：通常先应用 Yaw 再应用 Pitch
            parentWorldQuat.rotateY(yawRad).rotateX(pitchRad)
        } else {
            // 如果无法获取实体，回退到只使用 IAnimatable 提供的 Y 旋转
            parentWorldQuat.rotateY(animatable.getRootYRot(animatable.partialTicks))
            SparkCore.LOGGER.warn("IKApplier: Could not get Entity from IAnimatable, using only Y rotation for root.")
        }
        // --- 结束获取旋转 ---

        val calikoBones = chain.chain // 使用 getChain() 获取骨骼列表
        for (i in calikoBones.indices) {
            val fbBone = calikoBones[i]
            val boneName = fbBone.name ?: continue // Caliko 0.9.3 FabrikBone3D 有 getName()
            val sBone = bones[boneName] ?: continue // Spark Core Bone
            val oBone = model.getBone(boneName) ?: continue // Spark Core OBone (原始定义)

            // 1. 计算骨骼新的世界方向
            val startLoc = fbBone.startLocation // Caliko 0.9.3 有 getStartLocation()
            val endLoc = fbBone.endLocation     // Caliko 0.9.3 有 getEndLocation()
            val newWorldDir = Vector3f(endLoc.x - startLoc.x, endLoc.y - startLoc.y, endLoc.z - startLoc.z).normalize()

            // 2. 计算骨骼新的世界旋转四元数 (从参考轴旋转到新方向)
            val newWorldQuat = Quaternionf().rotationTo(BONE_REFERENCE_AXIS, newWorldDir)

            // 3. 计算骨骼新的局部旋转 (相对于父骨骼)
            // newLocal = inv(parentWorld) * newWorld
            val newLocalQuat = parentWorldQuat.invert(Quaternionf()).mul(newWorldQuat)

            // 4. 获取骨骼初始的局部旋转 (来自 OBone.rotation, 假设是 ZYX 欧拉角 - 度)
            val initialEulerDeg = oBone.rotation
            val initialEulerRad = Vector3f(
                Math.toRadians(initialEulerDeg.x).toFloat(),
                Math.toRadians(initialEulerDeg.y).toFloat(),
                Math.toRadians(initialEulerDeg.z).toFloat()
            )
            // 假设 OBone.rotation 是 ZYX 顺序
            val initialLocalQuat = Quaternionf().rotateZYX(initialEulerRad.z, initialEulerRad.y, initialEulerRad.x)

            // 5. 计算从初始局部旋转到新局部旋转的差值旋转 (Delta Rotation)
            // delta = inv(initialLocal) * newLocal
            val deltaQuat = initialLocalQuat.invert(Quaternionf()).mul(newLocalQuat)

            // 6. 将差值旋转转换为 ZYX 欧拉角 (弧度) - 这是 KeyAnimData.rotation 需要的格式
            // 注意：JOML 的 getEulerAnglesZYX 返回 Z, Y, X 顺序的弧度值
            val deltaEulerZYXRad = deltaQuat.getEulerAnglesZYX(Vector3f())
            // 转换为 Minecraft Vec3 (X, Y, Z 顺序)
            val keyDataRotation = Vec3(deltaEulerZYXRad.z.toDouble(), deltaEulerZYXRad.y.toDouble(), deltaEulerZYXRad.x.toDouble())

            // 7. 更新 Spark Core Bone 的 KeyAnimData
            val currentKeyData = sBone.data
            // 只更新旋转，保持位置和缩放不变 (IK 主要影响旋转)
            val newKeyData = currentKeyData.copy(rotation = keyDataRotation)
            sBone.updateInternal(newKeyData) // 使用内部更新方法

            // 8. 更新父骨骼的世界旋转，为下一次迭代做准备
            parentWorldQuat = newWorldQuat // 当前骨骼的世界旋转成为下一个子骨骼的父旋转
        }
    }
}
