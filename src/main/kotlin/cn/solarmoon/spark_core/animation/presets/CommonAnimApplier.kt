package cn.solarmoon.spark_core.animation.presets

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.state.AnimStateMachineManager
import cn.solarmoon.spark_core.entity.isAboveGround
import cn.solarmoon.spark_core.event.ChangePresetAnimEvent
import cn.solarmoon.spark_core.ik.caliko.IKApplier
import cn.solarmoon.spark_core.ik.caliko.IKResolver
import cn.solarmoon.spark_core.ik.component.IKHost
import cn.solarmoon.spark_core.physics.toVec3
import com.jme3.math.Vector3f
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.living.LivingEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import ru.nsk.kstatemachine.statemachine.processEventBlocking

object CommonAnimApplier {

    @SubscribeEvent
    private fun entityJoin(event: EntityJoinLevelEvent) {
        val entity = event.entity
        val level = event.level
        if (entity is LivingEntity && entity as? IEntityAnimatable<out LivingEntity> != null && !level.isClientSide) {
            AnimStateMachineManager.putStateMachine(entity, level, EntityStateAnimMachine.create(entity))
        }
    }

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity

        // 1. 更新动画控制器 (计算骨骼局部变换)
        if (entity is IEntityAnimatable<*>) {
            entity.animController.tick()
        }

//        Minecraft.getInstance().options.keyAttack.onEvent(KeyEvent.PRESS_ONCE) {
//            Minecraft.getInstance().player?.animController?.setAnimation("sword:combo_${entity.tickCount % 2}", 0)
//            true
//        }

        when {
            // 2. 处理状态机 (可能触发新的动画)
            entity is Player -> {
                // 强制玩家实体使用 PlayerStateAnimMachine
                AnimStateMachineManager.getStateMachine(entity)?.processEventBlocking(PlayerStateAnimMachine.SwitchEvent())
            }
            else -> {
                // 其他实体使用 EntityStateAnimMachine
                AnimStateMachineManager.getStateMachine(entity)?.processEventBlocking(EntityStateAnimMachine.SwitchEvent())
            }
        }

        // --- IK Update Logic (Server-Side) ---
        if (entity is IKHost<*> && !entity.level().isClientSide) {
            val ikHost = entity // Cast for clarity
            val physicsLevel = ikHost.physicsLevel // Assuming IKHost provides this

            // Check if physicsLevel and its world are initialized
            val world = physicsLevel.world
            val ikManager = ikHost.ikManager

            // 1. Prepare targets (perform ground checks)
            try {
                ikManager.prepareTargetsForPhysics(world)
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

                    // Solve IK
                    IKResolver.solveIK(ikHost, chainName, actualTargetForSolver)

                    // Apply IK result to the model
                    IKApplier.applyIKResult(ikHost, chainName)

                } catch (e: Exception) {
                    SparkCore.LOGGER.error("CommonAnimApplier Tick: Exception during IK solve/apply for component '$chainName' on ${entity.displayName?.string ?: entity.stringUUID}", e)
                }
            }

        }

        // 3. *** 提取期望的 IK 目标点 (使用 IAnimatable 提供的方法) ***
        if (entity is IKHost<*>) { // 确保是 IKHost
            val ikHost = entity

            // --- 示例：针对 "right_leg" IK 链 ---
            val rightLegChainName = "right_leg"
            val rightLegTargetBoneName = "rightLeg" // 末端骨骼名称，应与 IKComponentType 中定义一致

            try {
                // 使用 getWorldBonePivot 获取动画决定的期望世界位置
                // partialTick 设为 1f，因为我们在服务器 tick 中获取目标状态
                val desiredWorldPosJME = ikHost.getWorldBonePivot(rightLegTargetBoneName, Vec3.ZERO, 1f)
                val desiredWorldPosMC = desiredWorldPosJME.toVec3()

                // 存入 Map
                ikHost.ikTargetPositions[rightLegChainName] = desiredWorldPosMC
                // SparkCore.LOGGER.debug("Set desired IK target for $rightLegChainName: $desiredWorldPosMC")
            } catch (e: Exception) {
                // 获取骨骼位置可能失败（例如骨骼名错误），移除旧目标并记录错误
                ikHost.ikTargetPositions.remove(rightLegChainName)
                SparkCore.LOGGER.error("CommonAnimApplier Tick: Failed to get world pivot for bone '$rightLegTargetBoneName' on ${entity.displayName?.string ?: entity.stringUUID}", e)
            }
            // --- TODO: 为其他 IK 链（如左腿）重复此过程 ---
        }

        // 4. 执行 IK 更新逻辑 (服务器端)
        if (entity is IKHost<*> && !entity.level().isClientSide) {
            val ikHost = entity // Cast for clarity
            val physicsLevel = ikHost.physicsLevel // Assuming IKHost provides this

            // Check if physicsLevel and its world are initialized
            val world = physicsLevel.world
            val ikManager = ikHost.ikManager

            // 1. Prepare targets (perform ground checks) - 现在应该能读到 ikTargetPositions 了
            try {
                ikManager.prepareTargetsForPhysics(world)
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

                    // Solve IK
                    IKResolver.solveIK(ikHost, chainName, actualTargetForSolver)

                    // Apply IK result to the model
                    IKApplier.applyIKResult(ikHost, chainName)

                } catch (e: Exception) {
                    SparkCore.LOGGER.error("CommonAnimApplier Tick: Exception during IK solve/apply for component '$chainName' on ${entity.displayName?.string ?: entity.stringUUID}", e)
                }
            }
        }

        // 5. 其他 Tick 逻辑
         if (entity.jumpingLag && !entity.isAboveGround(0.1)) {
             entity.jumpingLag = false
//            if (entity is IEntityAnimatable<*>) {
//                val animName = "Common/jump_land"
//                val event = NeoForge.EVENT_BUS.post(ChangePresetAnimEvent.Common(entity, animName, CommonState.JUMP_LAND))
//                entity.animController.blendSpace.put(BlendAnimation(entity.newAnimInstance(event.newAnim ?: event.originAnim)))
//            }
        }

    }

    @SubscribeEvent
    private fun jump(event: LivingEvent.LivingJumpEvent) {
        val entity = event.entity
        if (entity !is IEntityAnimatable<*>) return
        entity.jumpingLag = true
        val animName = "Common/jump_start"
        val event = NeoForge.EVENT_BUS.post(ChangePresetAnimEvent.Common(entity, animName, CommonState.JUMP))
        entity.animController.setAnimation(event.newAnim ?: event.originAnim, 0)
    }
}