package cn.solarmoon.spark_core.ik.presets

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.event.ModelIndexChangeEvent
import cn.solarmoon.spark_core.ik.caliko.IKApplier
import cn.solarmoon.spark_core.ik.caliko.IKResolver
import cn.solarmoon.spark_core.ik.component.IKComponentType
import cn.solarmoon.spark_core.ik.origin.OIKConstraint
import cn.solarmoon.spark_core.ik.util.IKCoordinateTransformer
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.physics.toVec3
import com.jme3.math.Vector3f
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import kotlin.collections.forEach

/**
 * 玩家IK预设，包含了玩家IK系统的配置和实现。
 * 核心原则是：动画先计算基础姿势，IK后调整基础姿势。
 */
object PlayerIKPresets {

    @SubscribeEvent
    fun onEntityJoin(event: EntityJoinLevelEvent) {
        val entity = event.entity
        val level = event.level
        // 自动应用ModelIndex中的IK约束
        if(entity is IEntityAnimatable<*>) applyIKConstraintsFromModelIndex(entity as IEntityAnimatable<*>)
    }

    /**
     * 处理ModelIndex变化事件
     * 当实体的ModelIndex变化时，更新IK约束
     */
    @SubscribeEvent
    private fun onModelIndexChange(event: ModelIndexChangeEvent) {
        val animatable = event.animatable
        if (animatable is IEntityAnimatable<*>) {
            // 清除旧的IK组件
            clearIKComponents(animatable)
            // 应用新的IK约束
            applyIKConstraintsFromModelIndex(animatable)
        }
    }


    /**
     * 清除所有IK组件
     */
    private fun clearIKComponents(animatable: IEntityAnimatable<*>) {
        val ikManager = animatable.ikManager
        val componentNames = ikManager.activeComponents.keys.toList() // 创建副本以避免并发修改问题

        componentNames.forEach { chainName ->
            ikManager.removeComponent(chainName)
        }

        // 清除IK链和目标位置
        animatable.ikChains.clear()
        animatable.ikTargetPositions.clear()
    }

    /**
     * 从ModelIndex中加载并应用IK约束
     * 这个方法会检查实体的ModelIndex中的ikPath，并应用所有匹配的IK约束
     */
    private fun applyIKConstraintsFromModelIndex(animatable: IEntityAnimatable<*>) {
        // 获取ModelIndex中的IK约束
        val ikConstraints = animatable.ikConstraints
        if (ikConstraints.isEmpty()) {
            // 如果没有IK约束，直接返回
            return
        }

        SparkCore.LOGGER.info("应用来自ModelIndex的${ikConstraints.size}个IK约束到 ${animatable.animatable}")

        // 遍历所有IK约束并应用
        ikConstraints.forEach { constraint ->
            try {
                // 从约束创建IKComponentType
                val componentType = createIKComponentTypeFromConstraint(constraint)
                if (componentType != null) {
                    // 添加到IKManager
                    val success = animatable.ikManager.addComponent(componentType)
                    if (success) {
                        SparkCore.LOGGER.debug("成功应用IK约束: ${constraint.constraintName} 到 ${animatable.animatable}")
                    } else {
                        SparkCore.LOGGER.warn("无法应用IK约束: ${constraint.constraintName} 到 ${animatable.animatable}")
                    }
                }
            } catch (e: Exception) {
                SparkCore.LOGGER.error("应用IK约束时出错: ${constraint.constraintName}", e)
            }
        }
    }

    /**
     * 从OIKConstraint创建IKComponentType
     */
    private fun createIKComponentTypeFromConstraint(constraint: OIKConstraint): IKComponentType? {
        // 检查必要的字段
        if (constraint.constraintTargetBone.isBlank() || constraint.ikChainBoneLimits.isEmpty()) {
            SparkCore.LOGGER.warn("IK约束缺少必要字段: ${constraint.constraintName}")
            return null
        }

        // 构建骨骼路径
        val bonePathNames = constraint.ikChainBoneLimits.keys.toList()
        if (bonePathNames.isEmpty()) {
            SparkCore.LOGGER.warn("IK约束没有定义骨骼路径: ${constraint.constraintName}")
            return null
        }

        // 创建唯一的组件ID
        val componentId = ResourceLocation.fromNamespaceAndPath(
            "spark_core",
            "ik_constraints/${constraint.armatureName}/${constraint.constraintName}"
        )

        // 创建IKComponentType
        return IKComponentType(
            id = componentId,
            chainName = constraint.constraintName,
            startBoneName = bonePathNames.first(),
            endBoneName = bonePathNames[bonePathNames.size - 2], // 倒数第二个, 我们假设最后一个末端骨骼不参与动画
            bonePathNames = bonePathNames,
            defaultMaxIterations = constraint.iterations,
            defaultTolerance = 0.1f, // 使用默认值
            ikConstraintId = null, // 不需要引用自身

            poleTargetBoneName = constraint.poleTargetBone,
            poleAngleDegrees = constraint.poleAngleDeg
        )
    }
    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        // --- IK Update Logic (Server-Side) ---
        if (entity is IEntityAnimatable<*>) {
            val ikhost = entity // Cast for clarity
            val physicsLevel = ikhost.physicsLevel // Assuming IEntityAnimatable provides this

            // Check if physicsLevel and its world are initialized
            val world = physicsLevel.world
            val ikManager = ikhost.ikManager

            // 0. 更新所有IK组件的默认目标位置（从当前动画姿势中获取）
            ikManager.activeComponents.forEach { (chainName, component) ->
                try {
                    // 获取末端执行器骨骼名称
                    val endBoneName = component.targetBoneName

                    // 从当前动画姿势中获取末端执行器的本地位置
                    val worldPos = ikhost.getWorldBonePivot(endBoneName)

                    component.updateTargetWorldPosition(worldPos.toBVector3f())

                    // 将本地位置存入Map
                    ikhost.ikTargetPositions[chainName] = worldPos.toVec3()
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("Failed to update default target for component '$chainName' on ${entity.displayName?.string ?: entity.stringUUID}", e)
                }
            }

            // 1. Prepare targets (perform ground checks)
            try {
//                ikManager.prepareTargetsForPhysics(world)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("CommonAnimApplier Tick: Exception during prepareTargetsForPhysics for ${entity.displayName?.string ?: entity.stringUUID}", e)
            }

            // 2. Solve and Apply IK for each component using the actual targets
            ikManager.activeComponents.forEach { (chainName, component) ->
                try {
                    // Retrieve the actual target calculated by prepareTargetsForPhysics
                    val actualTargetJME: Vector3f = component.actualTargetPosition

                    // Convert JME Vector3f back to Minecraft Vec3
                    val actualTargetForSolver = Vec3(actualTargetJME.x.toDouble(), actualTargetJME.y.toDouble(), actualTargetJME.z.toDouble()) // Direct conversion

                    // 注意：actualTargetPosition已经是本地坐标，直接传给IKResolver
                    // Solve IK
                    IKResolver.solveIK(ikhost, chainName, actualTargetForSolver)

                    // Apply IK result to the model
                    IKApplier.applyIKResult(ikhost, chainName)

                } catch (e: Exception) {
                    SparkCore.LOGGER.error("CommonAnimApplier Tick: Exception during IK solve/apply for component '$chainName' on ${entity.displayName?.string ?: entity.stringUUID}", e)
                }
            }

        }
}
}