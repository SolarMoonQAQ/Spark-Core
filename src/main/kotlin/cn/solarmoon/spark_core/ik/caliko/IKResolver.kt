package cn.solarmoon.spark_core.ik.caliko

import au.edu.federation.caliko.FabrikChain3D
import au.edu.federation.utils.Vec3f
import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.ik.util.CalikoUtils.toCalikoVec3f
import net.minecraft.world.phys.Vec3
import au.edu.federation.utils.Vec3f as CalikoVec3f

/**
 * 处理 Caliko IK 解算的组件。
 */
object IKResolver {
    /**
     * 对指定的 IK 链执行一次 IK 解算。
     * 注意：此方法会直接修改传入的 AnimInstance 中的 FabrikChain3D 状态。
     * 该方法期望目标位置已经是本地坐标空间中的坐标。
     *
     * @param animatable 持有 IK 链的动画体实例。
     * @param chainName 要解算的 IK 链的名称。
     * @param targetPosition 末端效应器的目标坐标（已经是本地坐标空间）。
     * @return 返回解算后末端效应器与目标的距离。如果链未找到或解算过程中发生异常，则返回 Float.MAX_VALUE。
     */
    fun solveIK(
        animatable: IEntityAnimatable<*>,
        chainName: String,
        targetPosition: Vec3
        // iterations 和 tolerance 需要在 chain 对象上预先设置
    ): Float {
        SparkCore.LOGGER.trace(
            "IKResolver: Solving chain '{}' for target {}",
            chainName,
            targetPosition
        )
        val chain: FabrikChain3D? = animatable.ikChains[chainName] // Get chain from IAnimatable
        if (chain == null) { // 检查链是否存在
            SparkCore.LOGGER.warn("IKResolver: Chain '$chainName' not found in IAnimatable for ${animatable.animatable}")
            return Float.MAX_VALUE // 返回失败指示
        }

        // 将 Minecraft 的 Vec3 转换为 Caliko 的 Vec3f
        val targetPositionCaliko: CalikoVec3f = targetPosition.toCalikoVec3f() // 转换坐标

        // 获取组件类型，以便应用极向目标
        val component = animatable.ikManager.getComponent(chainName)
        val componentType = component?.type

        try {
            // 检查是否有极向目标需要应用
            var poleTarget: Vec3f? = null
            var poleAngle: Float? = null

            // 从组件类型中获取极向目标信息
            if (componentType != null) {
                // 检查组件类型中是否直接指定了极向目标
                if (componentType.poleTargetBoneName != null && componentType.poleAngleDegrees != null) {
                    val poleTargetBone = animatable.model.getBone(componentType.poleTargetBoneName)
                    if (poleTargetBone != null) {
                        poleTarget = poleTargetBone.pivot.toCalikoVec3f()
                        poleAngle = componentType.poleAngleDegrees
                    }
                }

                // 如果组件类型引用了IK约束，检查约束中是否指定了极向目标
                if (poleTarget == null && componentType.ikConstraintId != null) {
                    val ikConstraint = cn.solarmoon.spark_core.ik.origin.OIKConstraint.get(componentType.ikConstraintId)
                    if (ikConstraint != cn.solarmoon.spark_core.ik.origin.OIKConstraint.EMPTY) {
                        // 如果约束中指定了极向目标骨骼，尝试获取其世界坐标
                        if (ikConstraint.poleTargetBone != null && ikConstraint.poleTargetObject != null) {
                            val poleTargetBone = animatable.model.getBone(ikConstraint.poleTargetBone)
                            if (poleTargetBone != null) {
                                // 使用本地空间中的骨骼位置作为极向目标
                                poleTarget = poleTargetBone.pivot.toCalikoVec3f()
                                // 使用约束中的极向角度（如果有）
                                if (ikConstraint.poleAngleDeg != null) {
                                    poleAngle = ikConstraint.poleAngleDeg
                                } else if (ikConstraint.poleAngleRad != null) {
                                    poleAngle = Math.toDegrees(ikConstraint.poleAngleRad.toDouble()).toFloat()
                                }
                            }
                        }
                    }
                }
            }

            // 调用 Caliko 的解算方法，它期望世界坐标目标点
            val solveDistance: Float = chain.solveForTarget(targetPositionCaliko) // 调用解算
            // 如果找到了极向目标和角度，应用它们
            if (poleTarget != null && poleAngle != null) {
                CalikoStructureBuilder.applyPoleTarget(chain, poleTarget, poleAngle)
            }
            SparkCore.LOGGER.trace(
                "IKResolver: Solved chain '{}' for target {}. Result distance: {}",
                chainName,
                targetPosition,
                solveDistance
            ) // Use trace for frequent logs
            return solveDistance // 返回解算距离
        } catch (e: Exception) {
            SparkCore.LOGGER.error("IKResolver: Exception during solving chain '$chainName'", e)
            return Float.MAX_VALUE // 返回失败指示
        }
    }
}