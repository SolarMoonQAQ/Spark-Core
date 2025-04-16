package cn.solarmoon.spark_core.ik.caliko

import au.edu.federation.caliko.FabrikChain3D // 确保导入正确
import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import au.edu.federation.utils.Vec3f as CalikoVec3f
import cn.solarmoon.spark_core.ik.caliko.CalikoUtils.toCalikoVec3f // 确保导入正确
import cn.solarmoon.spark_core.ik.component.IKHost
import net.minecraft.world.phys.Vec3

/**
 * 处理 Caliko IK 解算的组件。
 */
object IKResolver {
    /**
     * 对指定的 IK 链执行一次 IK 解算。
     * 注意：此方法会直接修改传入的 AnimInstance 中的 FabrikChain3D 状态。
     *
     * @param animatable 持有 IK 链的动画体实例。
     * @param chainName 要解算的 IK 链的名称。
     * @param targetWorldPosition 末端效应器的目标世界坐标。
     * @return 返回解算后末端效应器与目标的距离。如果链未找到或解算过程中发生异常，则返回 Float.MAX_VALUE。
     */
    fun solveIK(
        animatable: IKHost<*>, // Changed parameter type
        chainName: String,
        targetWorldPosition: Vec3
        // iterations 和 tolerance 需要在 chain 对象上预先设置
    ): Float {
        val chain: FabrikChain3D? = animatable.ikChains[chainName] // Get chain from IAnimatable
        if (chain == null) { // 检查链是否存在
            SparkCore.LOGGER.warn("IKResolver: Chain '$chainName' not found in IAnimatable for ${animatable.animatable}")
            return Float.MAX_VALUE // 返回失败指示
        }

        // 将 Minecraft 的世界坐标 Vec3 转换为 Caliko 的 Vec3f
        val targetPositionCaliko: CalikoVec3f = targetWorldPosition.toCalikoVec3f() // 转换坐标

        try {
            // 调用 Caliko 的解算方法，它期望世界坐标目标点
            val solveDistance: Float = chain.solveForTarget(targetPositionCaliko) // 调用解算

            SparkCore.LOGGER.trace(
                "IKResolver: Solved chain '{}' for target {}. Result distance: {}",
                chainName,
                targetWorldPosition,
                solveDistance
            ) // Use trace for frequent logs
            return solveDistance // 返回解算距离
        } catch (e: Exception) {
            SparkCore.LOGGER.error("IKResolver: Exception during solving chain '$chainName'", e)
            return Float.MAX_VALUE // 返回失败指示
        }
    }
}