package cn.solarmoon.spark_core.ik.presets

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.ik.component.IKComponentType
import cn.solarmoon.spark_core.ik.component.IKHost
import com.jme3.math.Vector3f
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent

object PlayerIKPresets {

    // 定义右腿的 IK 组件类型 - 使用正确的构造函数参数
    private val RIGHT_LEG_IK_TYPE = IKComponentType(
        id = ResourceLocation.parse("spark_core:player_right_leg_ik"), // 使用 ResourceLocation，假设 modid 是 spark_core
        chainName = "right_leg",    // 在IKManager中引用的名称
        startBoneName = "body",     // 链的起始骨骼 (根据 player.json)
        endBoneName = "rightLeg",       // 链的末端骨骼 (根据 player.json)
        // 可选：明确指定骨骼路径。如果提供，buildChain 会优先使用这个路径
        // 即使提供了这个，构造函数仍需要 startBoneName 和 endBoneName
        bonePathNames = listOf("body", "rightLeg","surface")
    )

    @SubscribeEvent
    fun onEntityJoin(event: EntityJoinLevelEvent) {
        val entity = event.entity
        val level = event.level

        // 仅处理服务器端的玩家实体，并且该实体必须实现了 IKHost
        if (entity is Player && entity is IKHost<*>) {
            val ikHost = entity // 类型转换以便清晰调用
            val ikManager = ikHost.ikManager // 获取 IK 管理器 (由 PlayerMixin 提供)

            SparkCore.LOGGER.info("Applying IK presets for player: ${entity.gameProfile.name}")

            // 尝试添加右腿 IK 组件
            if (ikManager.addComponent(RIGHT_LEG_IK_TYPE)) {
                // 添加成功后，获取该组件实例并进行配置
                val rightLegComponent = ikManager.getComponent(RIGHT_LEG_IK_TYPE.chainName)
                if (rightLegComponent != null) {
                    rightLegComponent.stickToGround = true // 启用地形适应
                    // 可以根据需要调整这些参数
                    rightLegComponent.groundCheckOffset = Vector3f(0f, 0.3f, 0f) // 从脚踝上方 0.3m 开始检测
                    rightLegComponent.groundCheckDistance = 2.0f // 向下检测 2.0m
                    SparkCore.LOGGER.info("Enabled stickToGround for ${RIGHT_LEG_IK_TYPE.chainName} on ${entity.gameProfile.name}")
                } else {
                    SparkCore.LOGGER.warn("Failed to retrieve component ${RIGHT_LEG_IK_TYPE.chainName} after adding for ${entity.gameProfile.name}")
                }
            } else {
                SparkCore.LOGGER.warn("Failed to add IK component ${RIGHT_LEG_IK_TYPE.chainName} for ${entity.gameProfile.name}")
            }

            // TODO: 在这里可以为左腿或其他部位添加类似的 IK 组件
        }
    }
}